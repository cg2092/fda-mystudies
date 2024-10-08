/*
 * Copyright © 2017-2019 Harvard Pilgrim Health Care Institute (HPHCI) and its Contributors.
 * Copyright 2020-2021 Google LLC
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction, including
 * without limitation the rights to use, copy, modify, merge, publish, distribute, sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or substantial
 * portions of the Software.
 *
 * Funding Source: Food and Drug Administration (“Funding Agency”) effective 18 September 2014 as Contract no. HHSF22320140030I/HHSF22301006T (the “Prime Contract”).
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND NON-INFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM, OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 *
 */

package com.harvard.studyappmodule;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.BitmapFactory;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Build;
import android.os.Bundle;
import android.view.View;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;
import android.widget.Toast;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.AppCompatTextView;
import androidx.browser.customtabs.CustomTabsIntent;
import androidx.viewpager.widget.ViewPager;
import com.google.gson.ExclusionStrategy;
import com.google.gson.FieldAttributes;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.TypeAdapter;
import com.google.gson.reflect.TypeToken;
import com.google.gson.stream.JsonReader;
import com.google.gson.stream.JsonWriter;
import com.harvard.AppConfig;
import com.harvard.BuildConfig;
import com.harvard.R;
import com.harvard.eligibilitymodule.CustomViewTaskActivity;
import com.harvard.eligibilitymodule.StepsBuilder;
import com.harvard.gatewaymodule.CircleIndicator;
import com.harvard.storagemodule.DbServiceSubscriber;
import com.harvard.studyappmodule.activitybuilder.model.servicemodel.Steps;
import com.harvard.studyappmodule.consent.ConsentBuilder;
import com.harvard.studyappmodule.consent.CustomConsentViewTaskActivity;
import com.harvard.studyappmodule.consent.model.Consent;
import com.harvard.studyappmodule.consent.model.CorrectAnswerString;
import com.harvard.studyappmodule.consent.model.EligibilityConsent;
import com.harvard.studyappmodule.events.GetUserStudyInfoEvent;
import com.harvard.studyappmodule.events.GetUserStudyListEvent;
import com.harvard.studyappmodule.studymodel.ConsentDocumentData;
import com.harvard.studyappmodule.studymodel.Study;
import com.harvard.studyappmodule.studymodel.StudyHome;
import com.harvard.studyappmodule.studymodel.StudyList;
import com.harvard.usermodule.UserModulePresenter;
import com.harvard.usermodule.event.GetPreferenceEvent;
import com.harvard.usermodule.model.Apps;
import com.harvard.usermodule.webservicemodel.Studies;
import com.harvard.usermodule.webservicemodel.StudyData;
import com.harvard.utils.AppController;
import com.harvard.utils.CustomFirebaseAnalytics;
import com.harvard.utils.Logger;
import com.harvard.utils.NetworkChangeReceiver;
import com.harvard.utils.SharedPreferenceHelper;
import com.harvard.utils.Urls;
import com.harvard.utils.version.Version;
import com.harvard.utils.version.VersionChecker;
import com.harvard.webservicemodule.apihelper.ApiCall;
import com.harvard.webservicemodule.apihelper.ConnectionDetector;
import com.harvard.webservicemodule.apihelper.HttpRequest;
import com.harvard.webservicemodule.apihelper.Responsemodel;
import com.harvard.webservicemodule.events.ParticipantEnrollmentDatastoreConfigEvent;
import com.harvard.webservicemodule.events.StudyDatastoreConfigEvent;
import io.realm.Realm;
import io.realm.RealmList;
import io.realm.RealmObject;
import java.io.IOException;
import java.net.HttpURLConnection;
import java.util.HashMap;
import java.util.List;
import org.researchstack.backbone.step.Step;
import org.researchstack.backbone.task.OrderedTask;
import org.researchstack.backbone.task.Task;

public class StandaloneStudyInfoActivity extends AppCompatActivity
    implements ApiCall.OnAsyncRequestComplete, NetworkChangeReceiver.NetworkChangeCallback {

  private static final int JOIN_ACTION_SIGIN = 100;
  private static final int SPECIFIC_STUDY = 103;
  private static final int STUDY_INFO = 104;
  private static final int GET_CONSENT_DOC = 102;
  private static final int GET_PREFERENCES = 101;
  private static final int RESULT_CODE_UPGRADE = 105;
  private static final int CONSENT_RESPONSECODE = 203;
  private static final String CONSENT = "consent";
  private String eligibilityType = "";

  private RelativeLayout backBtn;
  private AppCompatTextView consentLayButton;
  private AppCompatTextView joinButton;
  private LinearLayout bottombar1;
  private RelativeLayout consentLay;
  private ConsentDocumentData consentDocumentData;
  private Study study;
  private StudyHome studyHome;
  private DbServiceSubscriber dbServiceSubscriber;
  private Realm realm;
  private RealmList<Studies> userPreferenceStudies;
  private EligibilityConsent eligibilityConsent;
  private static AlertDialog alertDialog;
  VersionReceiver versionReceiver;
  private String latestVersion;
  private boolean force = false;
  AlertDialog.Builder alertDialogBuilder;
  private CustomFirebaseAnalytics analyticsInstance;
  private TextView offlineIndicatior;
  private NetworkChangeReceiver networkChangeReceiver;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    super.onCreate(savedInstanceState);
    setContentView(R.layout.activity_standalone_study_info);
    analyticsInstance = CustomFirebaseAnalytics.getInstance(this);
    networkChangeReceiver = new NetworkChangeReceiver(this);

    dbServiceSubscriber = new DbServiceSubscriber();
    realm = AppController.getRealmobj(this);
    initializeXmlId();
    setFont();
    bindEvents();

    AppController.getHelperProgressDialog()
        .showProgress(StandaloneStudyInfoActivity.this, "", "", false);
    GetUserStudyListEvent getUserStudyListEvent = new GetUserStudyListEvent();
    HashMap<String, String> header = new HashMap();
    HashMap<String, String> params = new HashMap();
    params.put("studyId", AppConfig.StudyId);
    StudyDatastoreConfigEvent studyDatastoreConfigEvent =
        new StudyDatastoreConfigEvent(
            "get",
            Urls.SPECIFIC_STUDY + "?studyId=" + AppConfig.StudyId,
            SPECIFIC_STUDY,
            StandaloneStudyInfoActivity.this,
            Study.class,
            params,
            header,
            null,
            false,
            this);

    getUserStudyListEvent.setStudyDatastoreConfigEvent(studyDatastoreConfigEvent);
    StudyModulePresenter studyModulePresenter = new StudyModulePresenter();
    studyModulePresenter.performGetGateWayStudyList(getUserStudyListEvent);

    if (AppConfig.AppType.equalsIgnoreCase(getString(R.string.app_standalone))) {
      backBtn.setVisibility(View.GONE);
    }
  }

  private void initializeXmlId() {
    backBtn = (RelativeLayout) findViewById(R.id.backBtn);
    joinButton = (AppCompatTextView) findViewById(R.id.joinButton);
    consentLayButton = (AppCompatTextView) findViewById(R.id.consentLayButton);
    bottombar1 = (LinearLayout) findViewById(R.id.bottom_bar1);
    consentLay = (RelativeLayout) findViewById(R.id.consentLay);
    offlineIndicatior = findViewById(R.id.offlineIndicatior);
  }

  private void setFont() {
    joinButton.setTypeface(AppController.getTypeface(this, "regular"));
    consentLayButton.setTypeface(
        AppController.getTypeface(StandaloneStudyInfoActivity.this, "regular"));
  }

  private void bindEvents() {

    joinButton.setOnClickListener(
        new View.OnClickListener() {
          @Override
          public void onClick(View view) {
            Bundle eventProperties = new Bundle();
            eventProperties.putString(
                CustomFirebaseAnalytics.Param.BUTTON_CLICK_REASON, getString(R.string.join_study));
            analyticsInstance.logEvent(
                CustomFirebaseAnalytics.Event.ADD_BUTTON_CLICK, eventProperties);
            if (SharedPreferenceHelper.readPreference(
                    StandaloneStudyInfoActivity.this, getString(R.string.userid), "")
                .equalsIgnoreCase("")) {
              Toast.makeText(
                      StandaloneStudyInfoActivity.this,
                      "SignIn to Join the Study",
                      Toast.LENGTH_SHORT)
                  .show();
              SharedPreferenceHelper.writePreference(
                  StandaloneStudyInfoActivity.this,
                  getString(R.string.loginflow),
                  "StandaloneStudyInfo");
              SharedPreferenceHelper.writePreference(
                  StandaloneStudyInfoActivity.this, getString(R.string.logintype), "signIn");
              CustomTabsIntent customTabsIntent =
                  new CustomTabsIntent.Builder()
                      .setToolbarColor(getResources().getColor(R.color.colorAccent))
                      .setShowTitle(true)
                      .setCloseButtonIcon(
                          BitmapFactory.decodeResource(getResources(), R.drawable.backeligibility))
                      .setStartAnimations(
                          StandaloneStudyInfoActivity.this,
                          R.anim.slide_in_right,
                          R.anim.slide_out_left)
                      .setExitAnimations(
                          StandaloneStudyInfoActivity.this,
                          R.anim.slide_in_left,
                          R.anim.slide_out_right)
                      .build();
              Apps apps = dbServiceSubscriber.getApps(realm);
              customTabsIntent.intent.setData(
                  Uri.parse(
                      Urls.LOGIN_URL
                          .replace("$FromEmail", apps.getFromEmail())
                          .replace("$SupportEmail", apps.getSupportEmail())
                          .replace("$AppName", apps.getAppName())
                          .replace("$ContactEmail", apps.getContactUsEmail())));
              startActivity(customTabsIntent.intent);
            } else {
              loginCallback();
            }
          }
        });
  }

  private void callGetStudyInfoWebservice() {
    AppController.getHelperProgressDialog()
        .showProgress(StandaloneStudyInfoActivity.this, "", "", false);
    HashMap<String, String> header = new HashMap<>();
    String url = Urls.STUDY_INFO + "?studyId=" + AppConfig.StudyId;
    GetUserStudyInfoEvent getUserStudyInfoEvent = new GetUserStudyInfoEvent();
    StudyDatastoreConfigEvent studyDatastoreConfigEvent =
        new StudyDatastoreConfigEvent(
            "get",
            url,
            STUDY_INFO,
            StandaloneStudyInfoActivity.this,
            StudyHome.class,
            null,
            header,
            null,
            false,
            StandaloneStudyInfoActivity.this);

    getUserStudyInfoEvent.setStudyDatastoreConfigEvent(studyDatastoreConfigEvent);
    StudyModulePresenter studyModulePresenter = new StudyModulePresenter();
    studyModulePresenter.performGetGateWayStudyInfo(getUserStudyInfoEvent);
  }

  @Override
  public <T> void asyncResponse(T response, int responseCode) {
    if (responseCode == SPECIFIC_STUDY) {
      if (response != null) {
        study = (Study) response;
        AppController.getHelperProgressDialog().dismissDialog();
        if (!study.getStudies().isEmpty()) {
          dbServiceSubscriber.saveStudyListToDB(this, study);
          if (study.getStudies().get(0).getStatus().equalsIgnoreCase("active")) {
            callGetStudyInfoWebservice();
            if (study
                .getStudies()
                .get(0)
                .getStatus()
                .equalsIgnoreCase(getString(R.string.closed))) {
              joinButton.setVisibility(View.GONE);
            }
          } else {
            Toast.makeText(
                    this,
                    "This study is " + study.getStudies().get(0).getStatus(),
                    Toast.LENGTH_SHORT)
                .show();
            finish();
          }
        } else {
          Toast.makeText(this, "Study not found", Toast.LENGTH_SHORT).show();
          finish();
        }
      } else {
        AppController.getHelperProgressDialog().dismissDialog();
        Toast.makeText(
                StandaloneStudyInfoActivity.this, R.string.error_retriving_data, Toast.LENGTH_SHORT)
            .show();
        finish();
      }
    } else if (responseCode == STUDY_INFO) {
      studyHome = (StudyHome) response;
      if (studyHome != null) {

        HashMap<String, String> header = new HashMap<>();
        String url =
            Urls.GET_CONSENT_DOC
                + "?studyId="
                + AppConfig.StudyId
                + "&consentVersion=&activityId=&activityVersion=";
        GetUserStudyInfoEvent getUserStudyInfoEvent = new GetUserStudyInfoEvent();
        StudyDatastoreConfigEvent studyDatastoreConfigEvent =
            new StudyDatastoreConfigEvent(
                "get",
                url,
                GET_CONSENT_DOC,
                StandaloneStudyInfoActivity.this,
                ConsentDocumentData.class,
                null,
                header,
                null,
                false,
                StandaloneStudyInfoActivity.this);

        getUserStudyInfoEvent.setStudyDatastoreConfigEvent(studyDatastoreConfigEvent);
        StudyModulePresenter studyModulePresenter = new StudyModulePresenter();
        studyModulePresenter.performGetGateWayStudyInfo(getUserStudyInfoEvent);

        setViewPagerView(studyHome);
      } else {
        AppController.getHelperProgressDialog().dismissDialog();
        Toast.makeText(this, R.string.unable_to_parse, Toast.LENGTH_SHORT).show();
      }
    } else if (responseCode == GET_CONSENT_DOC) {
      AppController.getHelperProgressDialog().dismissDialog();
      consentDocumentData = (ConsentDocumentData) response;
      getStudyWebsiteNull();
      studyHome.setStudyId(AppConfig.StudyId);
      if (studyHome != null) {
        dbServiceSubscriber.saveStudyInfoToDB(this, studyHome);
      }
      if (consentDocumentData != null) {
        consentDocumentData.setStudyId(AppConfig.StudyId);
        dbServiceSubscriber.saveConsentDocumentToDB(this, consentDocumentData);
      }
      setViewPagerView(studyHome);
    } else if (responseCode == GET_PREFERENCES) {

      AppController.getHelperProgressDialog().dismissDialog();
      StudyData studies = (StudyData) response;
      String studyStatusCheck = "";
      if (studies != null) {
        studies.setUserId(
            AppController.getHelperSharedPreference()
                .readPreference(StandaloneStudyInfoActivity.this, getString(R.string.userid), ""));

        StudyData studyData = dbServiceSubscriber.getStudyPreferencesListFromDB(realm);
        if (studyData == null) {
          int size = studies.getStudies().size();
          for (int i = 0; i < size; i++) {
            if (!studies.getStudies().get(i).getStudyId().equalsIgnoreCase(AppConfig.StudyId)) {
              studies.getStudies().remove(i);
              size = size - 1;
              i--;
            }
          }
          dbServiceSubscriber.saveStudyPreferencesToDB(this, studies);
        } else {
          studies = studyData;
        }

        AppController.getHelperSharedPreference()
            .writePreference(
                StandaloneStudyInfoActivity.this,
                getString(R.string.title),
                "" + study.getStudies().get(0).getTitle());
        AppController.getHelperSharedPreference()
            .writePreference(
                StandaloneStudyInfoActivity.this,
                getString(R.string.status),
                "" + study.getStudies().get(0).getStatus());
        if (!studies.getStudies().isEmpty()) {
          AppController.getHelperSharedPreference()
              .writePreference(
                  StandaloneStudyInfoActivity.this,
                  getString(R.string.studyStatus),
                  "" + studies.getStudies().get(0).getStatus());
        } else {
          AppController.getHelperSharedPreference()
              .writePreference(
                  StandaloneStudyInfoActivity.this, getString(R.string.studyStatus), "yetToEnroll");
        }
        AppController.getHelperSharedPreference()
            .writePreference(
                StandaloneStudyInfoActivity.this, getString(R.string.position), "" + 0);
        AppController.getHelperSharedPreference()
            .writePreference(
                StandaloneStudyInfoActivity.this,
                getString(R.string.enroll),
                "" + study.getStudies().get(0).getSetting().isEnrolling());
        AppController.getHelperSharedPreference()
            .writePreference(
                StandaloneStudyInfoActivity.this,
                getString(R.string.studyVersion),
                "" + study.getStudies().get(0).getStudyVersion());

        userPreferenceStudies = studies.getStudies();
        StudyList studyList = dbServiceSubscriber.getStudiesDetails(AppConfig.StudyId, realm);
        for (int i = 0; i < userPreferenceStudies.size(); i++) {
          if (userPreferenceStudies.get(i).getStudyId().equalsIgnoreCase(AppConfig.StudyId)) {
            studyStatusCheck = userPreferenceStudies.get(i).getStatus();
          }
        }
        if (studyList != null) {
          if (studyStatusCheck.equalsIgnoreCase("enrolled")) {
            new CallConsentMetaData(false).execute();
          } else if (!studyList.getSetting().isEnrolling()) {
            Toast.makeText(getApplication(), R.string.study_no_enroll, Toast.LENGTH_SHORT).show();
          } else if (studyList.getStatus().equalsIgnoreCase(StudyFragment.PAUSED)) {
            Toast.makeText(getApplication(), R.string.study_paused, Toast.LENGTH_SHORT).show();
          } else {
            new CallConsentMetaData(false).execute();
          }
        } else {
          Toast.makeText(this, "No study present", Toast.LENGTH_SHORT).show();
        }
      } else {
        Toast.makeText(
                StandaloneStudyInfoActivity.this, R.string.unable_to_parse, Toast.LENGTH_SHORT)
            .show();
      }
    }
  }

  @Override
  public void onNetworkChanged(boolean status) {
    if (!status) {
      offlineIndicatior.setVisibility(View.VISIBLE);
      joinButton.setClickable(false);
      joinButton.setAlpha(.5F);
    } else {
      offlineIndicatior.setVisibility(View.GONE);
      joinButton.setClickable(true);
      joinButton.setAlpha(1F);
    }
  }

  private class CallConsentMetaData extends AsyncTask<String, Void, String> {
    String response = null;
    String responseCode = null;
    Responsemodel responseModel;
    boolean join;

    public CallConsentMetaData(boolean join) {
      this.join = join;
    }

    @Override
    protected String doInBackground(String... params) {
      ConnectionDetector connectionDetector =
          new ConnectionDetector(StandaloneStudyInfoActivity.this);

      String url =
          Urls.BASE_URL_STUDY_DATASTORE + Urls.CONSENT_METADATA + "?studyId=" + AppConfig.StudyId;
      if (connectionDetector.isConnectingToInternet()) {
        responseModel =
            HttpRequest.getRequest(url, new HashMap<String, String>(), "STUDY_DATASTORE");
        responseCode = responseModel.getResponseCode();
        response = responseModel.getResponseData();
        if (responseCode.equalsIgnoreCase("0") && response.equalsIgnoreCase("timeout")) {
          response = "timeout";
        } else if (responseCode.equalsIgnoreCase("0") && response.equalsIgnoreCase("")) {
          response = "error";
        } else if (Integer.parseInt(responseCode) >= 201
            && Integer.parseInt(responseCode) < 300
            && response.equalsIgnoreCase("")) {
          response = "No data";
        } else if (Integer.parseInt(responseCode) >= 400
            && Integer.parseInt(responseCode) < 500
            && response.equalsIgnoreCase("http_not_ok")) {
          response = "client error";
        } else if (Integer.parseInt(responseCode) >= 500
            && Integer.parseInt(responseCode) < 600
            && response.equalsIgnoreCase("http_not_ok")) {
          response = "server error";
        } else if (response.equalsIgnoreCase("http_not_ok")) {
          response = "Unknown error";
        } else if (Integer.parseInt(responseCode) == HttpURLConnection.HTTP_UNAUTHORIZED) {
          response = "session expired";
        } else if (Integer.parseInt(responseCode) == HttpURLConnection.HTTP_OK
            && !response.equalsIgnoreCase("")) {
          response = response;
        } else {
          response = getString(R.string.unknown_error);
        }
      }
      return response;
    }

    @Override
    protected void onPostExecute(String result) {
      AppController.getHelperProgressDialog().dismissDialog();

      if (response != null) {
        if (response.equalsIgnoreCase("session expired")) {
          AppController.getHelperProgressDialog().dismissDialog();
          AppController.getHelperSessionExpired(
              StandaloneStudyInfoActivity.this, "session expired");
        } else if (response.equalsIgnoreCase("timeout")) {
          AppController.getHelperProgressDialog().dismissDialog();
          Toast.makeText(
                  StandaloneStudyInfoActivity.this,
                  getResources().getString(R.string.connection_timeout),
                  Toast.LENGTH_SHORT)
              .show();
        } else if (Integer.parseInt(responseCode) == HttpURLConnection.HTTP_OK) {

          Gson gson =
              new GsonBuilder()
                  .setExclusionStrategies(
                      new ExclusionStrategy() {
                        @Override
                        public boolean shouldSkipField(FieldAttributes f) {
                          return f.getDeclaringClass().equals(RealmObject.class);
                        }

                        @Override
                        public boolean shouldSkipClass(Class<?> clazz) {
                          return false;
                        }
                      })
                  .registerTypeAdapter(
                      new TypeToken<RealmList<CorrectAnswerString>>() {}.getType(),
                      new TypeAdapter<RealmList<CorrectAnswerString>>() {

                        @Override
                        public void write(JsonWriter out, RealmList<CorrectAnswerString> value)
                            throws IOException {
                          // Ignore
                        }

                        @Override
                        public RealmList<CorrectAnswerString> read(JsonReader in)
                            throws IOException {
                          RealmList<CorrectAnswerString> list =
                              new RealmList<CorrectAnswerString>();
                          in.beginArray();
                          while (in.hasNext()) {
                            CorrectAnswerString surveyObjectString = new CorrectAnswerString();
                            surveyObjectString.setAnswer(in.nextString());
                            list.add(surveyObjectString);
                          }
                          in.endArray();
                          return list;
                        }
                      })
                  .create();
          eligibilityConsent = gson.fromJson(response, EligibilityConsent.class);
          if (eligibilityConsent != null) {
            eligibilityConsent.setStudyId(AppConfig.StudyId);
            saveConsentToDB(eligibilityConsent);

            if (join) {
              joinStudy();
            } else {
              if (userPreferenceStudies != null) {
                if (userPreferenceStudies.size() != 0) {
                  boolean studyIdPresent = false;
                  for (int i = 0; i < userPreferenceStudies.size(); i++) {
                    if (userPreferenceStudies
                        .get(i)
                        .getStudyId()
                        .equalsIgnoreCase(AppConfig.StudyId)) {
                      studyIdPresent = true;
                      if (userPreferenceStudies
                          .get(i)
                          .getStatus()
                          .equalsIgnoreCase(StudyFragment.IN_PROGRESS)) {
                        if (!consentDocumentData
                            .getConsent()
                            .getVersion()
                            .equalsIgnoreCase(userPreferenceStudies.get(i).getUserStudyVersion())) {
                          startConsent(
                              eligibilityConsent.getConsent(),
                              eligibilityConsent.getEligibility().getType());
                        } else {
                          Intent intent =
                              new Intent(StandaloneStudyInfoActivity.this, SurveyActivity.class);
                          intent.putExtra("studyId", AppConfig.StudyId);
                          startActivity(intent);
                          finish();
                        }
                      } else {
                        joinStudy();
                      }
                    }
                  }
                  if (!studyIdPresent) {
                    joinStudy();
                  }
                } else {
                  joinStudy();
                }
              } else {
                Toast.makeText(
                        StandaloneStudyInfoActivity.this,
                        R.string.error_retriving_data,
                        Toast.LENGTH_SHORT)
                    .show();
              }
            }
          } else {
            Toast.makeText(
                    StandaloneStudyInfoActivity.this,
                    R.string.error_retriving_data,
                    Toast.LENGTH_SHORT)
                .show();
          }
        } else {
          AppController.getHelperProgressDialog().dismissDialog();
          Toast.makeText(
                  StandaloneStudyInfoActivity.this,
                  getResources().getString(R.string.unable_to_retrieve_data),
                  Toast.LENGTH_SHORT)
              .show();
        }
      } else {
        AppController.getHelperProgressDialog().dismissDialog();
        Toast.makeText(
                StandaloneStudyInfoActivity.this,
                getString(R.string.unknown_error),
                Toast.LENGTH_SHORT)
            .show();
      }
    }

    @Override
    protected void onPreExecute() {
      AppController.getHelperProgressDialog()
          .showProgress(StandaloneStudyInfoActivity.this, "", "", false);
    }
  }

  private void joinStudy() {
    if (!study.getStudies().get(0).getSetting().isEnrolling()) {
      Toast.makeText(getApplication(), R.string.study_no_enroll, Toast.LENGTH_SHORT).show();
    } else if (study.getStudies().get(0).getStatus().equalsIgnoreCase(StudyFragment.PAUSED)) {
      Toast.makeText(getApplication(), R.string.study_paused, Toast.LENGTH_SHORT).show();
    } else {
      if (eligibilityConsent.getEligibility().getType().equalsIgnoreCase("token")) {
        Intent intent =
            new Intent(StandaloneStudyInfoActivity.this, EligibilityEnrollmentActivity.class);
        intent.putExtra("enrollmentDesc", eligibilityConsent.getEligibility().getTokenTitle());
        intent.putExtra("title", study.getStudies().get(0).getTitle());
        intent.putExtra("studyId", AppConfig.StudyId);
        intent.putExtra("eligibility", "token");
        intent.putExtra("type", "join");
        startActivity(intent);
      } else if (eligibilityConsent.getEligibility().getType().equalsIgnoreCase("test")) {

        RealmList<Steps> stepsRealmList = eligibilityConsent.getEligibility().getTest();
        StepsBuilder stepsBuilder = new StepsBuilder(this, stepsRealmList, false);
        OrderedTask task = new OrderedTask("Test", stepsBuilder.getsteps());

        Intent intent =
            CustomViewTaskActivity.newIntent(
                this,
                task,
                "",
                AppConfig.StudyId,
                eligibilityConsent.getEligibility(),
                study.getStudies().get(0).getTitle(),
                "",
                "",
                "test",
                "join");
        startActivity(intent);

      } else {
        Intent intent =
            new Intent(StandaloneStudyInfoActivity.this, EligibilityEnrollmentActivity.class);
        intent.putExtra("enrollmentDesc", eligibilityConsent.getEligibility().getTokenTitle());
        intent.putExtra("title", study.getStudies().get(0).getTitle());
        intent.putExtra("studyId", AppConfig.StudyId);
        intent.putExtra("eligibility", "combined");
        intent.putExtra("type", "join");
        startActivityForResult(intent, 12345);
      }
    }
  }

  private void saveConsentToDB(EligibilityConsent eligibilityConsent) {
    realm.beginTransaction();
    realm.copyToRealmOrUpdate(eligibilityConsent);
    realm.commitTransaction();
  }

  private void startConsent(Consent consent, String type) {
    eligibilityType = type;
    AppController.getHelperSharedPreference()
        .writePreference(this, "DataSharingScreen" + study.getStudies().get(0).getTitle(), "false");
    Toast.makeText(
            StandaloneStudyInfoActivity.this,
            getResources().getString(R.string.please_review_the_updated_consent),
            Toast.LENGTH_SHORT)
        .show();
    ConsentBuilder consentBuilder = new ConsentBuilder();
    List<Step> consentstep =
        consentBuilder.createsurveyquestion(
            StandaloneStudyInfoActivity.this, consent, study.getStudies().get(0).getTitle());
    Task consentTask = new OrderedTask(CONSENT, consentstep);
    Intent intent =
        CustomConsentViewTaskActivity.newIntent(
            StandaloneStudyInfoActivity.this,
            consentTask,
            AppConfig.StudyId,
            "",
            study.getStudies().get(0).getTitle(),
            eligibilityType,
            "update");
    startActivityForResult(intent, CONSENT_RESPONSECODE);
  }

  @Override
  protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
    super.onActivityResult(requestCode, resultCode, data);
    if (requestCode == JOIN_ACTION_SIGIN) {
      if (resultCode == RESULT_OK) {
        loginCallback();
      }
    } else if (requestCode == 12345) {
      if (resultCode == RESULT_OK) {
        if (eligibilityConsent != null) {
          RealmList<Steps> stepsRealmList = eligibilityConsent.getEligibility().getTest();
          StepsBuilder stepsBuilder = new StepsBuilder(this, stepsRealmList, false);
          OrderedTask task = new OrderedTask("Test", stepsBuilder.getsteps());

          Intent intent =
              CustomViewTaskActivity.newIntent(
                  this,
                  task,
                  "",
                  AppConfig.StudyId,
                  eligibilityConsent.getEligibility(),
                  study.getStudies().get(0).getTitle(),
                  data.getStringExtra("enrollId"),
                  data.getStringExtra("siteId"),
                  "combined",
                  "join");
          startActivity(intent);
        }
      }
    } else if (requestCode == CONSENT_RESPONSECODE) {
      if (resultCode == RESULT_OK) {
        Intent intent =
            new Intent(StandaloneStudyInfoActivity.this, ConsentCompletedActivity.class);
        intent.putExtra("studyId", AppConfig.StudyId);
        intent.putExtra("title", study.getStudies().get(0).getTitle());
        intent.putExtra("eligibility", eligibilityType);
        intent.putExtra("type", data.getStringExtra(CustomConsentViewTaskActivity.TYPE));
        // get the encrypted file path
        intent.putExtra("PdfPath", data.getStringExtra("PdfPath"));
        startActivity(intent);
        finish();
      } else {
        Toast.makeText(this, "Please complete the consent to continue", Toast.LENGTH_SHORT).show();
        finish();
      }
    } else if (requestCode == RESULT_CODE_UPGRADE) {
      Version currVer = new Version(AppController.currentVersion());
      Version latestVer = new Version(latestVersion);
      if (currVer.equals(latestVer) || currVer.compareTo(latestVer) > 0) {
        Logger.info(BuildConfig.APPLICATION_ID, "App Updated");
      } else {
        if (force) {
          Toast.makeText(
                  StandaloneStudyInfoActivity.this,
                  "Please update the app to continue using",
                  Toast.LENGTH_SHORT)
              .show();
          moveTaskToBack(true);
          if (Build.VERSION.SDK_INT < 21) {
            finishAffinity();
          } else {
            finishAndRemoveTask();
          }
        } else {
          AlertDialog.Builder alertDialogBuilder =
              new AlertDialog.Builder(StandaloneStudyInfoActivity.this, R.style.MyAlertDialogStyle);
          alertDialogBuilder.setTitle("Upgrade");
          alertDialogBuilder
              .setMessage("Please consider updating app next time")
              .setCancelable(false)
              .setPositiveButton(
                  "ok",
                  new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                      Bundle eventProperties = new Bundle();
                      eventProperties.putString(
                          CustomFirebaseAnalytics.Param.BUTTON_CLICK_REASON,
                          getString(R.string.app_update_next_time_ok));
                      analyticsInstance.logEvent(
                          CustomFirebaseAnalytics.Event.ADD_BUTTON_CLICK, eventProperties);
                      dialog.dismiss();
                    }
                  })
              .show();
        }
      }
    }
  }

  private void getStudyWebsiteNull() {
    joinButton.setVisibility(View.VISIBLE);
    boolean aboutThisStudy = false;
    if ((aboutThisStudy) && studyHome.getStudyWebsite().equalsIgnoreCase("")) {
      bottombar1.setVisibility(View.INVISIBLE);
      joinButton.setVisibility(View.INVISIBLE);
    } else if (aboutThisStudy) {
      bottombar1.setVisibility(View.VISIBLE);
      joinButton.setVisibility(View.INVISIBLE);
      if (studyHome.getStudyWebsite() != null
          && !studyHome.getStudyWebsite().equalsIgnoreCase("")) {
        consentLayButton.setText(getResources().getString(R.string.visit_website));
        consentLay.setOnClickListener(
            new View.OnClickListener() {
              @Override
              public void onClick(View v) {
                Bundle eventProperties = new Bundle();
                eventProperties.putString(
                    CustomFirebaseAnalytics.Param.BUTTON_CLICK_REASON,
                    getString(R.string.visit_website));
                analyticsInstance.logEvent(
                    CustomFirebaseAnalytics.Event.ADD_BUTTON_CLICK, eventProperties);
                Intent browserIntent =
                    new Intent(Intent.ACTION_VIEW, Uri.parse(studyHome.getStudyWebsite()));
                startActivity(browserIntent);
              }
            });
      } else {
        consentLay.setVisibility(View.INVISIBLE);
      }
    } else if (!studyHome.getStudyWebsite().equalsIgnoreCase("")) {
      bottombar1.setVisibility(View.VISIBLE);
      consentLayButton.setText(getResources().getString(R.string.visit_website));
      consentLay.setOnClickListener(
          new View.OnClickListener() {
            @Override
            public void onClick(View v) {
              Bundle eventProperties = new Bundle();
              eventProperties.putString(
                  CustomFirebaseAnalytics.Param.BUTTON_CLICK_REASON,
                  getString(R.string.visit_website));
              analyticsInstance.logEvent(
                  CustomFirebaseAnalytics.Event.ADD_BUTTON_CLICK, eventProperties);
              Intent browserIntent =
                  new Intent(Intent.ACTION_VIEW, Uri.parse(studyHome.getStudyWebsite()));
              startActivity(browserIntent);
            }
          });
    } else {
      bottombar1.setVisibility(View.INVISIBLE);
    }

    if (study.getStudies().get(0).getStatus().equalsIgnoreCase(getString(R.string.closed))) {
      joinButton.setVisibility(View.GONE);
    }
  }

  @Override
  public void asyncResponseFailure(int responseCode, String errormsg, String statusCode) {}

  private void setViewPagerView(final StudyHome studyHome) {

    ViewPager viewpager = (ViewPager) findViewById(R.id.viewpager);
    CircleIndicator indicator = (CircleIndicator) findViewById(R.id.indicator);
    viewpager.setAdapter(
        new StudyInfoPagerAdapter(
            StandaloneStudyInfoActivity.this, studyHome.getInfo(), AppConfig.StudyId));
    indicator.setViewPager(viewpager);
    if (studyHome.getInfo().size() < 2) {
      indicator.setVisibility(View.GONE);
    }
    viewpager.setCurrentItem(0);
    indicator.setOnPageChangeListener(
        new ViewPager.OnPageChangeListener() {
          public void onPageScrollStateChanged(int state) {}

          public void onPageScrolled(
              int position, float positionOffset, int positionOffsetPixels) {}

          public void onPageSelected(int position) {
            // Check if this is the page you want.
            if (studyHome.getInfo().get(position).getType().equalsIgnoreCase("video")) {
              joinButton.setBackground(getResources().getDrawable(R.drawable.rectangle_blue_white));
              joinButton.setTextColor(getResources().getColor(R.color.white));
            } else {
              joinButton.setBackground(
                  getResources().getDrawable(R.drawable.rectangle_black_white));
              joinButton.setTextColor(getResources().getColor(R.color.colorPrimary));
            }
          }
        });
  }

  private void loginCallback() {
    AppController.getHelperProgressDialog()
        .showProgress(StandaloneStudyInfoActivity.this, "", "", false);

    HashMap<String, String> header = new HashMap();
    header.put(
        "Authorization",
        "Bearer "
            + AppController.getHelperSharedPreference()
            .readPreference(
                StandaloneStudyInfoActivity.this, getResources().getString(R.string.auth), ""));
    header.put(
        "userId",
        AppController.getHelperSharedPreference()
            .readPreference(
                StandaloneStudyInfoActivity.this, getResources().getString(R.string.userid), ""));

    header.put("deviceType", android.os.Build.MODEL);
    header.put("deviceOS", Build.VERSION.RELEASE);
    header.put("mobilePlatform", "ANDROID");

    ParticipantEnrollmentDatastoreConfigEvent participantDatastoreConfigEvent =
        new ParticipantEnrollmentDatastoreConfigEvent(
            "get",
            Urls.STUDY_STATE,
            GET_PREFERENCES,
            StandaloneStudyInfoActivity.this,
            StudyData.class,
            null,
            header,
            null,
            false,
            this);
    GetPreferenceEvent getPreferenceEvent = new GetPreferenceEvent();
    getPreferenceEvent.setParticipantEnrollmentDatastoreConfigEvent(
        participantDatastoreConfigEvent);
    UserModulePresenter userModulePresenter = new UserModulePresenter();
    userModulePresenter.performGetUserPreference(getPreferenceEvent);
  }

  @Override
  protected void onResume() {
    super.onResume();
    IntentFilter filter = new IntentFilter();
    filter.addAction(BuildConfig.APPLICATION_ID);
    versionReceiver = new VersionReceiver();
    registerReceiver(versionReceiver, filter);

    IntentFilter intentFilter = new IntentFilter(ConnectivityManager.CONNECTIVITY_ACTION);
    registerReceiver(networkChangeReceiver, intentFilter);
  }

  @Override
  protected void onPause() {
    super.onPause();

    try {
      unregisterReceiver(versionReceiver);
    } catch (Exception e) {
      e.printStackTrace();
    }
    try {
      if (alertDialog != null) {
        alertDialog.dismiss();
      }
    } catch (Exception e) {
      e.printStackTrace();
    }
    if (networkChangeReceiver != null) {
      unregisterReceiver(networkChangeReceiver);
    }
  }

  public class VersionReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
      if (intent.getStringExtra("api").equalsIgnoreCase("success")) {
        Version currVer = new Version(AppController.currentVersion());
        Version latestVer = new Version(intent.getStringExtra("latestVersion"));

        latestVersion = intent.getStringExtra("latestVersion");
        force = Boolean.parseBoolean(intent.getStringExtra("force"));

        if (currVer.equals(latestVer) || currVer.compareTo(latestVer) > 0) {
          isUpgrade(false, latestVersion, force);
        } else {
          AppController.getHelperSharedPreference()
              .writePreference(StandaloneStudyInfoActivity.this, "versionalert", "done");
          isUpgrade(true, latestVersion, force);
        }
      } else {
        // commented because if impleting the offline indicator

        //        Toast.makeText(StandaloneStudyInfoActivity.this, "Error detected",
        // Toast.LENGTH_SHORT).show();
        //        if (Build.VERSION.SDK_INT < 21) {
        //          finishAffinity();
        //        } else {
        //          finishAndRemoveTask();
        //        }
      }
    }
  }

  public void isUpgrade(boolean b, String latestVersion, final boolean force) {
    this.latestVersion = latestVersion;
    this.force = force;
    String msg;
    String positiveButton;
    String negativeButton;
    if (b) {
      if (force) {
        msg = "Please upgrade the app to continue.";
        positiveButton = "Ok";
        negativeButton = "Cancel";
      } else {
        msg = "A new version of this app is available. Do you want to update it now?";
        positiveButton = "Yes";
        negativeButton = "Skip";
      }
      alertDialogBuilder =
          new AlertDialog.Builder(StandaloneStudyInfoActivity.this, R.style.MyAlertDialogStyle);
      alertDialogBuilder.setTitle("Upgrade");
      alertDialogBuilder
          .setMessage(msg)
          .setCancelable(false)
          .setPositiveButton(
              positiveButton,
              new DialogInterface.OnClickListener() {
                public void onClick(DialogInterface dialog, int id) {
                  Bundle eventProperties = new Bundle();
                  eventProperties.putString(
                      CustomFirebaseAnalytics.Param.BUTTON_CLICK_REASON,
                      getString(R.string.app_upgrade_ok));
                  analyticsInstance.logEvent(
                      CustomFirebaseAnalytics.Event.ADD_BUTTON_CLICK, eventProperties);
                  startActivityForResult(
                      new Intent(Intent.ACTION_VIEW, Uri.parse(VersionChecker.PLAY_STORE_URL)),
                      RESULT_CODE_UPGRADE);
                }
              })
          .setNegativeButton(
              negativeButton,
              new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                  Bundle eventProperties = new Bundle();
                  eventProperties.putString(
                      CustomFirebaseAnalytics.Param.BUTTON_CLICK_REASON,
                      getString(R.string.app_upgrade_cancel));
                  analyticsInstance.logEvent(
                      CustomFirebaseAnalytics.Event.ADD_BUTTON_CLICK, eventProperties);
                  dialog.dismiss();
                  if (force) {
                    Toast.makeText(
                            StandaloneStudyInfoActivity.this,
                            "Please update the app to continue using",
                            Toast.LENGTH_SHORT)
                        .show();
                    moveTaskToBack(true);
                    if (Build.VERSION.SDK_INT < 21) {
                      finishAffinity();
                    } else {
                      finishAndRemoveTask();
                    }
                  } else {
                    dialog.dismiss();
                  }
                }
              });
      alertDialog = alertDialogBuilder.create();
      alertDialog.show();
    }
  }
}
