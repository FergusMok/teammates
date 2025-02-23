package teammates.ui.webapi;

import java.util.List;

import org.testng.annotations.Test;

import teammates.common.datatransfer.attributes.FeedbackSessionAttributes;
import teammates.common.util.Const;
import teammates.common.util.EmailType;
import teammates.common.util.EmailWrapper;
import teammates.common.util.TaskWrapper;
import teammates.common.util.TimeHelper;
import teammates.common.util.TimeHelperExtension;
import teammates.test.ThreadHelper;
import teammates.ui.request.SendEmailRequest;

/**
 * SUT: {@link FeedbackSessionClosingRemindersAction}.
 */
public class FeedbackSessionClosingRemindersActionTest
        extends BaseActionTest<FeedbackSessionClosingRemindersAction> {

    @Override
    protected String getActionUri() {
        return Const.CronJobURIs.AUTOMATED_FEEDBACK_CLOSING_REMINDERS;
    }

    @Override
    protected String getRequestMethod() {
        return GET;
    }

    @Override
    @Test
    protected void testAccessControl() {
        verifyOnlyAdminCanAccess();
    }

    @Override
    @Test
    public void testExecute() throws Exception {

        ______TS("default state of typical data bundle: 0 sessions closing soon");

        FeedbackSessionClosingRemindersAction action = getAction();
        action.execute();

        verifyNoTasksAdded();

        ______TS("1 session closing soon, 1 session closing soon with disabled closing reminder, "
                 + "1 session closing soon but not yet opened");

        // Modify session to close in 24 hours

        FeedbackSessionAttributes session1 = typicalBundle.feedbackSessions.get("session1InCourse1");
        session1.setTimeZone("UTC");
        session1.setStartTime(TimeHelper.getInstantDaysOffsetFromNow(-1));
        session1.setEndTime(TimeHelper.getInstantDaysOffsetFromNow(1));
        logic.updateFeedbackSession(
                FeedbackSessionAttributes
                        .updateOptionsBuilder(session1.getFeedbackSessionName(), session1.getCourseId())
                        .withTimeZone(session1.getTimeZone())
                        .withStartTime(session1.getStartTime())
                        .withEndTime(session1.getEndTime())
                        .build());
        session1.setSentOpeningSoonEmail(true); // fsLogic will set the flag to true
        session1.setSentOpenEmail(true); // fsLogic will set the flag to true
        verifyPresentInDatabase(session1);

        // Ditto, but disable the closing reminder

        FeedbackSessionAttributes session2 = typicalBundle.feedbackSessions.get("session1InCourse2");
        session2.setTimeZone("UTC");
        session2.setStartTime(TimeHelper.getInstantDaysOffsetFromNow(-1));
        session2.setEndTime(TimeHelper.getInstantDaysOffsetFromNow(1));
        session2.setClosingEmailEnabled(false);
        logic.updateFeedbackSession(
                FeedbackSessionAttributes
                        .updateOptionsBuilder(session2.getFeedbackSessionName(), session2.getCourseId())
                        .withTimeZone(session2.getTimeZone())
                        .withStartTime(session2.getStartTime())
                        .withEndTime(session2.getEndTime())
                        .withIsClosingEmailEnabled(session2.isClosingEmailEnabled())
                        .build());
        session2.setSentOpeningSoonEmail(true); // fsLogic will set the flag to true
        session2.setSentOpenEmail(true); // fsLogic will set the flag to true
        verifyPresentInDatabase(session2);

        // 1 session not yet opened; do not send the closing reminder

        FeedbackSessionAttributes session3 = typicalBundle.feedbackSessions.get("gracePeriodSession");
        session3.setTimeZone("UTC");
        session3.setStartTime(TimeHelperExtension.getInstantHoursOffsetFromNow(1));
        session3.setEndTime(TimeHelper.getInstantDaysOffsetFromNow(1));
        logic.updateFeedbackSession(
                FeedbackSessionAttributes
                        .updateOptionsBuilder(session3.getFeedbackSessionName(), session3.getCourseId())
                        .withTimeZone(session3.getTimeZone())
                        .withStartTime(session3.getStartTime())
                        .withEndTime(session3.getEndTime())
                        .build());
        session3.setSentOpeningSoonEmail(true); // fsLogic will set the flag to true
        session3.setSentOpenEmail(false); // fsLogic will set the flag to true
        verifyPresentInDatabase(session3);

        // wait for very briefly so that the above session will be within the time limit
        ThreadHelper.waitFor(5);

        action = getAction();
        action.execute();

        // 5 students, 5 instructors, and 3 co-owner instructors in course1
        verifySpecifiedTasksAdded(Const.TaskQueue.SEND_EMAIL_QUEUE_NAME, 13);

        String courseName = logic.getCourse(session1.getCourseId()).getName();
        List<TaskWrapper> tasksAdded = mockTaskQueuer.getTasksAdded();
        for (TaskWrapper task : tasksAdded) {
            SendEmailRequest requestBody = (SendEmailRequest) task.getRequestBody();
            EmailWrapper email = requestBody.getEmail();
            String expectedSubject = (email.getIsCopy() ? EmailWrapper.EMAIL_COPY_SUBJECT_PREFIX : "")
                    + String.format(EmailType.FEEDBACK_CLOSING.getSubject(),
                    courseName, session1.getFeedbackSessionName());
            assertEquals(expectedSubject, email.getSubject());
        }

        ______TS("1 session closing soon with emails sent");

        session1.setSentClosingEmail(true);
        logic.updateFeedbackSession(
                FeedbackSessionAttributes
                        .updateOptionsBuilder(session3.getFeedbackSessionName(), session3.getCourseId())
                        .withSentClosingEmail(session3.isSentClosingEmail())
                        .build());

        action = getAction();
        action.execute();

        verifyNoTasksAdded();

    }

}
