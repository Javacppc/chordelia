package org.gresch.quintett.service;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.gresch.quintett.domain.kombination.AkkordIdRangeZwoelftonklaenge;
import org.gresch.quintett.domain.kombination.Kombinationsberechnung;
import org.gresch.quintett.domain.tonmodell.Akkord;
import org.gresch.quintett.persistence.AkkordDao;
import org.hibernate.FlushMode;
import org.hibernate.ScrollableResults;
import org.hibernate.SessionFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import javax.annotation.Resource;
import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.List;
import java.util.Set;

// FIXME Wirklich Trennung von Persistieren und Berechnen?
// FIXME Mir scheint dieser Service überflüssig zu sein.
@Service("akkordKombinationenService")
public class AkkordKombinationenServiceImpl implements AkkordKombinationenService {

  //  @Resource(name = "tonService")
  //  private TonService tonService;

  public final static Log log = LogFactory.getLog(AkkordKombinationenServiceImpl.class);
  @PersistenceContext
  EntityManager entityManager;
  @Resource(name = "kombinationsberechnungService")
  private KombinationsberechnungService kombinationsberechnungService;
  @Resource(name = "akkordkombinationenBerechnungService")
  private AkkordkombinationenBerechnungService akkordkombinationenBerechnungService;
  @Resource(name = "akkordDao")
  private AkkordDao akkordDao;

  public AkkordKombinationenServiceImpl() {
    // Beany baby
  }

  @Override
  @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
  public ScrollableResults getScrollableResultByBasisAkkordRange(Integer minBlockId, Integer maxBlockId, int fetchBlockGroesze, boolean absteigend) {
    return akkordDao.getScrollableResultByBasisAkkordRange(minBlockId, maxBlockId, fetchBlockGroesze, absteigend);

  }

  // FIXME Set oder doch TreeSet, ausnahmsweise?
  @Override
  @Transactional
  public Set<Akkord> getAkkordkombinationenZuBasisAkkord(Akkord basisAkkord) {
    return akkordDao.getAkkordkombinationenZuBasisAkkord(basisAkkord);
  }

  @Override
  @Transactional(propagation = Propagation.REQUIRED, isolation = Isolation.READ_COMMITTED)
  public int berechneUndPersistiereKombinationsberechnung() throws Exception {
  /* TODO
     * - Prüfe, ob DB erstellen gewählt wurde
     * - wenn ja, lasse runIncrementorToeneZwei laufen und danach
     * - hole bis zu einem maximalwert (z.B. 1000) immer die nächsten basisakkord-IDs
     * - Zwischenstand persistieren. Nicht einen einzigen Flush. Ggf. mit Propagationlevel arbeiten.
     *  
     */
    int anzahlBerechneteAkkorde = -1;

    // Sollte keinen Unterschied im Vergleich zur Übergabe machen.
    Kombinationsberechnung kombinationsberechnung = kombinationsberechnungService.getKombinationsBerechnung();

    if (kombinationsberechnung.getHatPersistenzSchreiben() && (kombinationsberechnung.getHatPersistenzLaden() == false)) {
      int incrementorToene = -1;
      Integer bereitsBerechneteToene = kombinationsberechnung.getBereitsBerechneteToene();
      // Prüfen: bereits berechnete Töne <== max. Töne
      assert (!(null == bereitsBerechneteToene));
      int anzahlAkkorde = -1;
      // TODO bereitsBerechneteAkkorde/bereitsBerechneteAkkordId, darauf mit Range-Umrechnung.
      int incrementorToeneStartwert = bereitsBerechneteToene.intValue() + 1;
      // Setzte auf Zweitonklänge, da Töne separat initialisiert werden.
      if (incrementorToeneStartwert == 0) {
        incrementorToeneStartwert = 2;
      }
      final int maxAnzahlToene = kombinationsberechnung.getMaxAnzahlToene().intValue();
      for (incrementorToene = incrementorToeneStartwert; incrementorToene <= maxAnzahlToene; incrementorToene++) {
        //        log.info("berechneUndPersistiereKombinationen(~): incrementorToene = " + incrementorToene);
        if (incrementorToene == 2) {
          anzahlAkkorde = akkordkombinationenBerechnungService.runIncrementorToeneZwei();
          kombinationsberechnung.setLetzteAkkordId(anzahlAkkorde);
          kombinationsberechnung.setBereitsBerechneteToene(incrementorToene);
          kombinationsberechnungService.saveKombinationsBerechnung(kombinationsberechnung);
          //          FlushMode flushModeOld = entityManager.unwrap(SessionFactory.class).getCurrentSession().getFlushMode();
          //          entityManager.unwrap(SessionFactory.class).getCurrentSession().setFlushMode(FlushMode.MANUAL);
          //          entityManager.unwrap(SessionFactory.class).getCurrentSession().flush();
          //          entityManager.unwrap(SessionFactory.class).getCurrentSession().setFlushMode(flushModeOld);
        } else {
          int basisAkkordIdStart = AkkordIdRangeZwoelftonklaenge.minIdZuAnzahlToene(incrementorToene - 1);
          int basisAkkordIdEnde = AkkordIdRangeZwoelftonklaenge.maxIdZuAnzahlToene(incrementorToene - 1);
          incrementorToene = AkkordIdRangeZwoelftonklaenge.anzahlToeneZuId(basisAkkordIdStart) + 1;
          // TODO Parameter!!!
          Integer fetchBlockGroesze = 50;
          Integer minBlockId = basisAkkordIdStart;
          Integer naechsteMinBlockId = -1;
          Integer maxBlockId = -1;
          Boolean weiterenBlockLaden = true;

          while (weiterenBlockLaden) {
            if (!(minBlockId.equals(basisAkkordIdStart) && (naechsteMinBlockId.equals(-1)))) {
              // Nur erster Durchgang
              minBlockId = naechsteMinBlockId;
            }

            if (!((minBlockId + fetchBlockGroesze) > basisAkkordIdEnde)) {
              maxBlockId = minBlockId + fetchBlockGroesze;
              naechsteMinBlockId = maxBlockId + 1;
            } else {
              maxBlockId = basisAkkordIdEnde;
              weiterenBlockLaden = false;
            }
            // hole zu incrementorToene die minAkkordId und die maxAkkordId
            //          int minBasisAkkordId = -1;
            //          int maxBasisAkkordId = -1;
            //          minBasisAkkordId = AkkordIdRangeZwoelftonklaenge.minIdZuAnzahlToene(incrementorToene-1);
            //          maxBasisAkkordId = AkkordIdRangeZwoelftonklaenge.maxIdZuAnzahlToene(incrementorToene-1);
            //            log.info("berechneUndPersistiereKombinationen(~): minBlockId = " + minBlockId);
            //            log.info("berechneUndPersistiereKombinationen(~): maxBlockId = " + maxBlockId);
            // Hm, das sollte es auch nicht sein...
            int tempAnzahlAkkorde = -1;
            tempAnzahlAkkorde = anzahlAkkorde;
            // New thread?
            tempAnzahlAkkorde = akkordkombinationenBerechnungService
              .berechneUndPersistiereNachBasisAkkordBlock(minBlockId, maxBlockId, incrementorToene, tempAnzahlAkkorde);
            anzahlAkkorde = tempAnzahlAkkorde;
            //            log.info("berechneUndPersistiereKombinationen(~): anzahlAkkorde = " + anzahlAkkorde);
            kombinationsberechnung.setLetzteAkkordId(anzahlAkkorde);
            kombinationsberechnung.setBereitsBerechneteToene(incrementorToene);
            kombinationsberechnungService.saveKombinationsBerechnung(kombinationsberechnung);

            FlushMode flushModeOld = entityManager.unwrap(SessionFactory.class).getCurrentSession().getFlushMode();
            entityManager.unwrap(SessionFactory.class).getCurrentSession().setFlushMode(FlushMode.MANUAL);
            entityManager.unwrap(SessionFactory.class).getCurrentSession().flush();
            //            entityManager.unwrap(SessionFactory.class).getCurrentSession().getTransaction().commit();
            entityManager.unwrap(SessionFactory.class).getCurrentSession().setFlushMode(flushModeOld);
            //            log.info("flushKombinationsberechnung (1)");
          } // while
        } // else [>2]
      } // Ende incrementorToene-Schleife
      kombinationsberechnung = null;
      anzahlBerechneteAkkorde = anzahlAkkorde;
      //      log.info("berechneUndPersistiereKombinationen(~): anzahlBerechneteAkkorde = " + anzahlBerechneteAkkorde);
    } else {
      throw new RuntimeException(
        "Noch nicht implementiert! [!(kombinationsberechnung.getHatPersistenzSchreiben() && (!kombinationsberechnung.getHatPersistenzLaden()))]");
    }
    return anzahlBerechneteAkkorde;
  }

  @Override
  @Transactional
  public Akkord getAkkordById(Integer akkordId) {
    return akkordDao.find(akkordId);
  }

  @Override
  @Transactional
  public List<Integer> getAkkordIdsByAnzahlToene(int anzahlToene, boolean klangschaerfeAbsteigend, boolean akkordIdAbsteigend) {
    return akkordDao.getAkkordIdsByAnzahlToene(anzahlToene, klangschaerfeAbsteigend, akkordIdAbsteigend);
  }

  @Override
  @Transactional
  public List<Integer> getAkkordIdsByRange(int minId, int maxId, boolean klangschaerfeAbsteigend, boolean akkordIdAbsteigend) {
    return akkordDao.getAkkordIdsByRange(minId, maxId, klangschaerfeAbsteigend, akkordIdAbsteigend);
  }

}