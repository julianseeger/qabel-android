package de.qabel.qabelbox.ui;

import android.content.Intent;
import android.support.test.InstrumentationRegistry;
import android.support.test.espresso.intent.Intents;
import android.support.test.espresso.intent.rule.IntentsTestRule;

import org.junit.Test;

import de.qabel.desktop.repository.IdentityRepository;
import de.qabel.qabelbox.R;
import de.qabel.qabelbox.TestConstants;
import de.qabel.qabelbox.activities.MainActivity;
import de.qabel.qabelbox.config.AppPreference;
import de.qabel.qabelbox.persistence.RepositoryFactory;

import static android.support.test.espresso.Espresso.onView;
import static android.support.test.espresso.action.ViewActions.click;
import static android.support.test.espresso.assertion.ViewAssertions.matches;
import static android.support.test.espresso.contrib.DrawerActions.openDrawer;
import static android.support.test.espresso.intent.matcher.IntentMatchers.hasComponent;
import static android.support.test.espresso.intent.matcher.IntentMatchers.hasFlag;
import static android.support.test.espresso.matcher.ViewMatchers.isDisplayed;
import static android.support.test.espresso.matcher.ViewMatchers.withText;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.empty;
import static org.hamcrest.Matchers.not;
import static org.hamcrest.Matchers.notNullValue;
import static org.hamcrest.Matchers.nullValue;
import static org.junit.Assert.assertThat;

public class LogoutUITest extends AccountUITest {
    public static final String ACCOUNT_NAME = "account_name";
    public static final String ACCOUNT_E_MAIL = "accountmail@example.com";
    IntentsTestRule<MainActivity> mainActivityActivityTestRule =
            new MainActivityWithoutFilesFragmentTestRule();

    @Test
    public void testLogout() throws Exception {
        setAccountPreferences();
        appPreference.setToken(TestConstants.TOKEN);
        mainActivityActivityTestRule.launchActivity(null);
        openDrawer(R.id.drawer_layout);
        onView(withText(R.string.logout))
                .check(matches(isDisplayed()))
                .perform(click());
        Intents.intended(allOf(
                hasFlag(Intent.FLAG_ACTIVITY_CLEAR_TASK),
                hasFlag(Intent.FLAG_ACTIVITY_TASK_ON_HOME),
                hasComponent("de.qabel.qabelbox.activities.CreateAccountActivity")));
        onView(withText(R.string.create_account_login_infos)).check(matches(isDisplayed()));

        assertIdentitiesNotDeleted();
        assertThat("Login token not deleted", appPreference.getToken(), nullValue());
        assertThat("Login name deleted", appPreference.getAccountName(), notNullValue());
        assertThat("Login email deleted", appPreference.getAccountEMail(), notNullValue());
    }

    public void assertIdentitiesNotDeleted() throws Exception {
        RepositoryFactory factory = new RepositoryFactory(
                InstrumentationRegistry.getTargetContext());
        IdentityRepository identityRepository = factory.getIdentityRepository(
                factory.getAndroidClientDatabase());
        assertThat(identityRepository.findAll().getIdentities(), not(empty()));
    }

}
