/*
 * Copyright 2020-2021 Google LLC
 *
 * Use of this source code is governed by an MIT-style
 * license that can be found in the LICENSE file or at
 * https://opensource.org/licenses/MIT.
 */

package com.google.cloud.healthcare.fdamystudies.dao;

import static com.google.cloud.healthcare.fdamystudies.common.CommonConstants.CONSENT_TYPE;
import static com.google.cloud.healthcare.fdamystudies.common.CommonConstants.PRIMARY;
import static com.google.cloud.healthcare.fdamystudies.common.CommonConstants.STUDY_ID;

import com.google.api.services.healthcare.v1.model.Consent;
import com.google.cloud.healthcare.fdamystudies.common.EnrollmentStatus;
import com.google.cloud.healthcare.fdamystudies.config.ApplicationPropertyConfiguration;
import com.google.cloud.healthcare.fdamystudies.mapper.ConsentManagementAPIs;
import com.google.cloud.healthcare.fdamystudies.model.ParticipantRegistrySiteEntity;
import com.google.cloud.healthcare.fdamystudies.model.ParticipantStudyEntity;
import com.google.cloud.healthcare.fdamystudies.model.StudyEntity;
import com.google.cloud.healthcare.fdamystudies.util.MyStudiesUserRegUtil;
import java.sql.Timestamp;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.CriteriaUpdate;
import javax.persistence.criteria.Predicate;
import javax.persistence.criteria.Root;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.slf4j.ext.XLogger;
import org.slf4j.ext.XLoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Repository;

@Repository
public class StudyStateDaoImpl implements StudyStateDao {

  private static final XLogger logger =
      XLoggerFactory.getXLogger(StudyStateDaoImpl.class.getName());

  @Autowired private SessionFactory sessionFactory;

  @Autowired private ConsentManagementAPIs consentApis;

  @Autowired ApplicationPropertyConfiguration appConfig;

  @Override
  public String saveParticipantStudies(List<ParticipantStudyEntity> participantStudiesList) {
    logger.entry("Begin saveParticipantStudies()");
    Session session = this.sessionFactory.getCurrentSession();
    for (ParticipantStudyEntity participantStudy : participantStudiesList) {
      if (StringUtils.isEmpty(participantStudy.getId())) {
        session.save(participantStudy);
      } else {
        session.update(participantStudy);
      }
    }
    logger.exit("saveParticipantStudies() - Ends ");
    return MyStudiesUserRegUtil.ErrorCodes.SUCCESS.getValue();
  }

  @Override
  public String getEnrollTokenForParticipant(String participantRegistryId) {
    logger.entry("Begin getEnrollTokenForParticipant()");
    String enrolledToken = "";
    CriteriaBuilder criteriaBuilder = null;
    CriteriaQuery<ParticipantRegistrySiteEntity> criteriaQuery = null;
    Root<ParticipantRegistrySiteEntity> root = null;
    Predicate[] predicates = new Predicate[1];
    List<ParticipantRegistrySiteEntity> participantRegistryList = null;
    ParticipantRegistrySiteEntity participantRegistrySite = null;

    Session session = this.sessionFactory.getCurrentSession();
    criteriaBuilder = session.getCriteriaBuilder();
    criteriaQuery = criteriaBuilder.createQuery(ParticipantRegistrySiteEntity.class);
    root = criteriaQuery.from(ParticipantRegistrySiteEntity.class);
    predicates[0] = criteriaBuilder.equal(root.get("id"), participantRegistryId);
    criteriaQuery.select(root).where(predicates);
    participantRegistryList = session.createQuery(criteriaQuery).getResultList();
    if (!participantRegistryList.isEmpty()) {
      participantRegistrySite = participantRegistryList.get(0);
      enrolledToken = participantRegistrySite.getEnrollmentToken();
    }

    logger.exit("getEnrollTokenForParticipant() - Ends ");
    return enrolledToken;
  }

  @Override
  public String withdrawFromStudy(String participantId, String studyId) {
    logger.entry("Begin withdrawFromStudy()");
    String message = MyStudiesUserRegUtil.ErrorCodes.FAILURE.getValue();
    CriteriaBuilder criteriaBuilder = null;

    CriteriaQuery<StudyEntity> studyEntityCriteria = null;
    Root<StudyEntity> studyEntityRoot = null;
    Predicate[] studyEntityPredicates = new Predicate[1];
    List<StudyEntity> studiesBoList = null;
    StudyEntity studyEntity = null;

    CriteriaUpdate<ParticipantStudyEntity> criteriaUpdate = null;
    Root<ParticipantStudyEntity> participantStudyRoot = null;
    List<Predicate> predicates = new ArrayList<>();
    int isUpdated = 0;

    Session session = this.sessionFactory.getCurrentSession();

    criteriaBuilder = session.getCriteriaBuilder();

    studyEntityCriteria = criteriaBuilder.createQuery(StudyEntity.class);
    studyEntityRoot = studyEntityCriteria.from(StudyEntity.class);
    studyEntityPredicates[0] = criteriaBuilder.equal(studyEntityRoot.get("customId"), studyId);
    studyEntityCriteria.select(studyEntityRoot).where(studyEntityPredicates);
    studiesBoList = session.createQuery(studyEntityCriteria).getResultList();

    if (!studiesBoList.isEmpty()) {
      studyEntity = studiesBoList.get(0);
      criteriaUpdate = criteriaBuilder.createCriteriaUpdate(ParticipantStudyEntity.class);
      participantStudyRoot = criteriaUpdate.from(ParticipantStudyEntity.class);
      criteriaUpdate.set("status", EnrollmentStatus.WITHDRAWN.getStatus());
      criteriaUpdate.set("withdrawalDate", new Timestamp(Instant.now().toEpochMilli()));
      predicates.add(
          criteriaBuilder.equal(participantStudyRoot.get("participantId"), participantId));
      predicates.add(criteriaBuilder.equal(participantStudyRoot.get("study"), studyEntity));
      criteriaUpdate.where(predicates.toArray(new Predicate[predicates.size()]));
      isUpdated = session.createQuery(criteriaUpdate).executeUpdate();
      if (isUpdated > 0) {
        message = MyStudiesUserRegUtil.ErrorCodes.SUCCESS.getValue();
        if (Boolean.valueOf(appConfig.getEnableConsentManagementAPI())) {
          revokeConsent(studyEntity, participantId);
        }
      }
    }

    logger.exit("withdrawFromStudy() - Ends ");
    return message;
  }
  /**
   * revoke the consent
   *
   * @param studyEntity
   * @param participantId
   */
  private void revokeConsent(StudyEntity studyEntity, String participantId) {
    logger.entry("Begin revokeConsent()");
    String parentName =
        String.format(
            "projects/%s/locations/%s/datasets/%s/consentStores/%s",
            appConfig.getProjectId(),
            appConfig.getRegionId(),
            studyEntity.getCustomId(),
            "CONSENT_" + studyEntity.getCustomId());

    String filter1 = "Metadata(\"" + STUDY_ID + "\")=\"" + studyEntity.getCustomId() + "\"";
    String filter2 = "user_id=\"" + participantId + "\"";
    String filter3 = "Metadata(\"" + CONSENT_TYPE + "\")=\"" + PRIMARY + "\"";
    String consentFilter = filter1 + " AND " + filter2 + " AND " + filter3;

    List<Consent> list = consentApis.getListOfConsents(consentFilter, parentName);
    if (CollectionUtils.isNotEmpty(list)) {
      Consent consent = list.get(0);
      // updating consent state to REVOKED
      consentApis.revokeConsent(consent.getName());
    }
    logger.exit("revokeConsent() - Ends ");
  }
}
