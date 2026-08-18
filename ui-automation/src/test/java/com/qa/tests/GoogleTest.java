package com.qa.tests;

import org.junit.jupiter.api.Test;
import org.openqa.selenium.WebDriver;
import org.openqa.selenium.chrome.ChromeDriver;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class GoogleTest {

    @Test
    void verifyGoogleTitle() {

        WebDriver driver = new ChromeDriver();

        try {
            driver.get("https://www.google.com");

            String title = driver.getTitle();

            System.out.println("Page Title: " + title);

            assertTrue(title.toLowerCase().contains("google"));

        } finally {
            driver.quit();
        }
    }
}