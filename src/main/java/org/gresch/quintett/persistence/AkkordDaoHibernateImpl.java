package org.gresch.quintett.persistence;

import org.apache.commons.logging.Log;
import org.apache.commons.logging.LogFactory;
import org.gresch.quintett.domain.tonmodell.Akkord;
import org.hibernate.Criteria;
import org.hibernate.Query;
import org.hibernate.ScrollableResults;
import org.hibernate.Session;

import javax.persistence.EntityManager;
import javax.persistence.PersistenceContext;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

// @Repository("akkordDao")
public class AkkordDaoHibernateImpl /* extends AkkordDao<Akkord, Integer>*/ {

  @PersistenceContext
  EntityManager entityManager;
  private Log log = LogFactory.getLog(AkkordDaoHibernateImpl.class);

  public AkkordDaoHibernateImpl() {
    // Beany baby
  }

  public void makePersistentReadOnly(Akkord akkord) {
    // TODO Ggf. prüfen, ob FlushMode.MANUAL hier nicht besser wäre.

    try {
      Session session = entityManager.unwrap(org.hibernate.Session.class);
      session.saveOrUpdate(akkord);
      session.persist(akkord);
    } catch (Exception e) {
      log.error("Konnte Akkord nicht persistieren. Fehler war: " + e.getLocalizedMessage());
    }
  }

  public Set<Akkord> getAkkordkombinationenZuBasisAkkord(Akkord basisAkkord) {
    // TODO Null-Check etc.
    Criteria criteria = entityManager.unwrap(org.hibernate.Session.class).createCriteria(Akkord.class);
    criteria.createAlias("eq", "basisAkkordId");
    @SuppressWarnings("unchecked")
    List<Akkord> akkordList = criteria.list();
    Set<Akkord> akkordSet = Collections.synchronizedSortedSet(new TreeSet<Akkord>());
    for (Akkord akkord : akkordList) {
      akkordSet.add(akkord);
    }
    return akkordSet;
  }

  // XXX TODO Auf- oder absteigend : Klangschärfe und AkkordId wohl getrennt !?
  public ScrollableResults getScrollableResultByBasisAkkordRange(Integer minBlockId, Integer maxBlockId, int fetchBlockGroesze, boolean absteigend) {
    String steigendString = absteigend ? "DESC" : "ASC";
    String queryString = "";
    queryString = "FROM Akkord akkord WHERE akkord.id between " + minBlockId + " and " + maxBlockId + " ORDER BY akkord.anzahlToene, akkord.klangschaerfe "
      + steigendString + ", akkord.id " + steigendString;
    Query akkordResultQuery = entityManager.unwrap(org.hibernate.Session.class).createQuery(queryString);
    //     akkordResultQuery.setFetchSize(fetchBlockGroesze);
    akkordResultQuery.setReadOnly(true);
    //   akkordResultQuery.setCacheMode(CacheMode.IGNORE);
    akkordResultQuery.setComment("Scrollable results by basis akkord range");
    ScrollableResults akkordCursor = akkordResultQuery.scroll();
    return akkordCursor;
  }

  public List<Integer> getAkkordIdsByAnzahlToene(int anzahlToene, boolean klangschaerfeAbsteigend, boolean akkordIdAbsteigend) {
    String klangschaerfeAbsteigendString = klangschaerfeAbsteigend ? "DESC" : "ASC";
    String akkordIdAbsteigendString = akkordIdAbsteigend ? "DESC" : "ASC";
    String queryString = "";
    queryString = "SELECT akkord.id FROM Akkord akkord ORDER BY akkord.anzahlToene, akkord.klangschaerfe " + akkordIdAbsteigendString + ", akkord.id "
      + klangschaerfeAbsteigendString;
    Query akkordResultQuery = entityManager.unwrap(org.hibernate.Session.class).createQuery(queryString);
    akkordResultQuery.setReadOnly(true);
    @SuppressWarnings("unchecked")
    List<Integer> resultList = akkordResultQuery.list();
    return resultList;
  }

  public List<Integer> getAkkordIdsByRange(int minId, int maxId, boolean klangschaerfeAbsteigend, boolean akkordIdAbsteigend) {
    String steigendString = klangschaerfeAbsteigend ? "DESC" : "ASC";
    String akkordIdAbsteigendString = akkordIdAbsteigend ? "DESC" : "ASC";
    String queryString = "";
    queryString =
      "SELECT akkord.id FROM Akkord akkord WHERE akkord.id between " + minId + " and " + maxId + " ORDER BY akkord.anzahlToene, akkord.klangschaerfe "
        + steigendString + ", akkord.id " + akkordIdAbsteigendString;
    Query akkordResultQuery = entityManager.unwrap(org.hibernate.Session.class).createQuery(queryString);
    akkordResultQuery.setReadOnly(true);
    @SuppressWarnings("unchecked")
    List<Integer> resultList = akkordResultQuery.list();
    return resultList;
  }


}
