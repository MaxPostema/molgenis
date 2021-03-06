package org.molgenis.jobs.model;

import static org.apache.commons.lang3.StringUtils.abbreviate;
import static org.apache.commons.lang3.StringUtils.isEmpty;
import static org.apache.commons.lang3.StringUtils.join;
import static org.molgenis.jobs.model.JobExecutionMetaData.END_DATE;
import static org.molgenis.jobs.model.JobExecutionMetaData.FAILURE_EMAIL;
import static org.molgenis.jobs.model.JobExecutionMetaData.IDENTIFIER;
import static org.molgenis.jobs.model.JobExecutionMetaData.LOG;
import static org.molgenis.jobs.model.JobExecutionMetaData.PROGRESS_INT;
import static org.molgenis.jobs.model.JobExecutionMetaData.PROGRESS_MAX;
import static org.molgenis.jobs.model.JobExecutionMetaData.PROGRESS_MESSAGE;
import static org.molgenis.jobs.model.JobExecutionMetaData.RESULT_URL;
import static org.molgenis.jobs.model.JobExecutionMetaData.SCHEDULED_JOB_ID;
import static org.molgenis.jobs.model.JobExecutionMetaData.START_DATE;
import static org.molgenis.jobs.model.JobExecutionMetaData.STATUS;
import static org.molgenis.jobs.model.JobExecutionMetaData.SUBMISSION_DATE;
import static org.molgenis.jobs.model.JobExecutionMetaData.SUCCESS_EMAIL;
import static org.molgenis.jobs.model.JobExecutionMetaData.TYPE;
import static org.molgenis.jobs.model.JobExecutionMetaData.USER;

import java.time.Instant;
import java.util.Optional;
import javax.annotation.CheckForNull;
import javax.annotation.Nullable;
import org.apache.commons.lang3.StringUtils;
import org.molgenis.data.Entity;
import org.molgenis.data.meta.model.EntityType;
import org.molgenis.data.support.StaticEntity;

/**
 * Superclass that represents a job execution.
 *
 * <p>Do not add abstract identifier to this class, see EntitySerializerTest
 */
@SuppressWarnings("unused")
public class JobExecution extends StaticEntity {
  public static final String TRUNCATION_BANNER = "<<< THIS LOG HAS BEEN TRUNCATED >>>";
  /** If the progress message is larger than this value, it will be abbreviated. */
  public static final int MAX_PROGRESS_MESSAGE_LENGTH = 255;
  /**
   * If log is larger than this value, it will be truncated. This value shouldn't exceed the max
   * length of the {@link org.molgenis.data.meta.AttributeType#TEXT}
   */
  public static final int MAX_LOG_LENGTH = 256000;

  private boolean logTruncated = false;

  public JobExecution(Entity entity) {
    super(entity);
  }

  public JobExecution(EntityType entityType) {
    super(entityType);
  }

  public JobExecution(String identifier, EntityType entityType) {
    super(entityType);
    setIdentifier(identifier);
  }

  public String getIdentifier() {
    return getString(IDENTIFIER);
  }

  public void setIdentifier(String value) {
    set(IDENTIFIER, value);
  }

  public Optional<String> getUser() {
    return Optional.ofNullable(getString(USER));
  }

  public void setUser(@Nullable @CheckForNull String username) {
    set(USER, username);
  }

  public Status getStatus() {
    return Status.valueOf(getString(STATUS));
  }

  public void setStatus(Status value) {
    set(STATUS, value.toString().toUpperCase());
  }

  public String getType() {
    return getString(TYPE);
  }

  public void setType(String value) {
    set(TYPE, value);
  }

  public Instant getSubmissionDate() {
    return getInstant(SUBMISSION_DATE);
  }

  public void setSubmissionDate(Instant value) {
    set(SUBMISSION_DATE, value);
  }

  @Nullable
  @CheckForNull
  public Instant getStartDate() {
    return getInstant(START_DATE);
  }

  public void setStartDate(Instant value) {
    set(START_DATE, value);
  }

  @Nullable
  @CheckForNull
  public Instant getEndDate() {
    return getInstant(END_DATE);
  }

  public void setEndDate(Instant value) {
    set(END_DATE, value);
  }

  @Nullable
  @CheckForNull
  public Integer getProgressInt() {
    return getInt(PROGRESS_INT);
  }

  public void setProgressInt(Integer value) {
    set(PROGRESS_INT, value);
  }

  @Nullable
  @CheckForNull
  public String getProgressMessage() {
    return getString(PROGRESS_MESSAGE);
  }

  public void setProgressMessage(String value) {
    set(PROGRESS_MESSAGE, StringUtils.abbreviate(value, MAX_PROGRESS_MESSAGE_LENGTH));
  }

  @Nullable
  @CheckForNull
  public Integer getProgressMax() {
    return getInt(PROGRESS_MAX);
  }

  public void setProgressMax(Integer value) {
    set(PROGRESS_MAX, value);
  }

  @Nullable
  @CheckForNull
  public String getLog() {
    return getString(LOG);
  }

  private void setLog(String value) {
    set(LOG, value);
  }

  @Nullable
  @CheckForNull
  public String getResultUrl() {
    return getString(RESULT_URL);
  }

  public void setResultUrl(String value) {
    set(RESULT_URL, value);
  }

  public String[] getSuccessEmail() {
    String email = getString(SUCCESS_EMAIL);
    if (isEmpty(email)) {
      return new String[] {};
    }
    return email.split(",");
  }

  public String[] getFailureEmail() {
    String email = getString(FAILURE_EMAIL);
    if (isEmpty(email)) {
      return new String[] {};
    }
    return email.split(",");
  }

  public void setSuccessEmail(String successEmail) {
    set(SUCCESS_EMAIL, successEmail);
  }

  public void setFailureEmail(String failureEmail) {
    set(FAILURE_EMAIL, failureEmail);
  }

  public void setScheduledJobId(String scheduledJobId) {
    set(SCHEDULED_JOB_ID, scheduledJobId);
  }

  @Nullable
  @CheckForNull
  public String getScheduledJobId() {
    return getString(SCHEDULED_JOB_ID);
  }

  /**
   * Appends a log message to the execution log. The first time the log exceeds MAX_LOG_LENGTH, it
   * gets truncated and the TRUNCATION_BANNER gets added. Subsequent calls to appendLog will be
   * ignored.
   *
   * @param formattedMessage The formatted message to append to the log.
   */
  void appendLog(String formattedMessage) {
    if (logTruncated) return;
    String combined = join(getLog(), formattedMessage);
    if (combined.length() > MAX_LOG_LENGTH) {
      String truncated = abbreviate(combined, MAX_LOG_LENGTH - TRUNCATION_BANNER.length() * 2 - 2);
      combined = join(new String[] {TRUNCATION_BANNER, truncated, TRUNCATION_BANNER}, "\n");
      logTruncated = true;
    }
    setLog(combined);
  }

  public enum Status {
    PENDING,
    RUNNING,
    CANCELING,
    SUCCESS,
    FAILED,
    CANCELED
  }
}
