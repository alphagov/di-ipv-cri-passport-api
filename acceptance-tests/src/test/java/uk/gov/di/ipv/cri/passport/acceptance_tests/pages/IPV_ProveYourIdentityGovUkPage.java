package uk.gov.di.ipv.cri.passport.acceptance_tests.pages;

import org.openqa.selenium.By;

public class IPV_ProveYourIdentityGovUkPage extends IPV_GlobalPage {
    private static final By USER_INFO = By.cssSelector(".govuk-heading-l");
    public void waitForPageToLoad() {
        waitForElementVisible(USER_INFO, 30);
    }
}
