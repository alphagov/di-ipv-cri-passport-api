package uk.gov.di.ipv.cri.passport.acceptance_tests.pages;

import org.openqa.selenium.By;
import org.openqa.selenium.Keys;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.WebElement;
import uk.gov.di.ipv.cri.passport.acceptance_tests.utilities.IPV_PageObjectSupport;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.NoSuchFileException;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.Map;

public class IPV_GlobalPage extends IPV_PageObjectSupport {

    static final By CONTINUE_BUTTON = By.xpath("//button[@class='govuk-button button']");

    private static Map<String, String> jsonFileResponses = new HashMap<>();

    WebDriver driver;

    public IPV_GlobalPage() {
        this.driver = getCurrentDriver();
    }

    public void clickContinue() {
        clickElement(CONTINUE_BUTTON);
    }

    public String getCurrentPageUrl() {
        return getCurrentDriver().getCurrentUrl();
    }

    public void populateDetailsInFields(By detailsSelector, String fieldValue) {
        waitForElementVisible(detailsSelector, 60);
        WebElement field = getCurrentDriver().findElement(detailsSelector);
        field.sendKeys(Keys.HOME, Keys.chord(Keys.SHIFT, Keys.END), fieldValue);
    }

    public void populateField(By selector, String value) {
        waitForElementVisible(selector, 60);
        WebElement field = getCurrentDriver().findElement(selector);
        field.sendKeys(value);
    }

    public static String generateStringFromJsonPayloadResource(
            String jsonResourcePath, String fileName) throws IOException {
        if (jsonFileResponses.containsKey(fileName)) {
            return jsonFileResponses.get(fileName);
        }
        String jsonPayloadString = "";
        try {
            jsonPayloadString =
                    new String(Files.readAllBytes(Paths.get(jsonResourcePath + fileName + ".json")))
                            .replaceAll("\n", "");
            System.out.println("Json Payload Path is: " + jsonResourcePath + fileName + ".json");
            jsonFileResponses.put(fileName, jsonPayloadString);
        } catch (NoSuchFileException e) {
            jsonPayloadString =
                    new String(
                                    Files.readAllBytes(
                                            Paths.get(
                                                    jsonResourcePath
                                                            + "JSON/"
                                                            + fileName
                                                            + ".json")))
                            .replaceAll("\n", "");
            System.out.println(
                    "Json Payload Path is: " + jsonResourcePath + "JSON/" + fileName + ".json");
            jsonFileResponses.put(fileName, jsonPayloadString);
        }
        return jsonPayloadString;
    }

//    public static Integer extractIntegerValueFromJsonString(String jsonString, String jsonPath) {
//        return com.jayway.jsonpath.JsonPath.read(jsonString, jsonPath);
//    }
}
