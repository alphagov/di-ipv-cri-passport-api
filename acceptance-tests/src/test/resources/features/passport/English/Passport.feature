@hmpoDVAD
Feature: Passport Test

  Background:
    Given I navigate to the IPV Core Stub
    And I click the passport CRI for the testEnvironment
    And I search for passport user number 5 in the Experian table
    Then I check the page title is Enter your details exactly as they appear on your UK passport – GOV.UK One Login
    And I assert the url path contains details
    And I set the document checking route

  @build @staging @integration @smoke @stub @uat
  Scenario Outline: Passport details page happy path
    Given User enters data as a <PassportSubject>
    When User clicks on continue
    Then I navigate to the passport verifiable issuer to check for a Valid response
    And JSON payload should contain validity score 2 and strength score 4
    And JSON response should contain documentNumber 321654987 same as given passport
    And Passport VC should contain JTI field
#    And exp should be absent in the JSON payload
    And The test is complete and I close the driver
    Examples:
      | PassportSubject             |
      | PassportSubjectHappyKenneth |


  @build @staging @integration @smoke @stub @uat
  Scenario Outline: Passport test
    Given User enters data as a <PassportSubject>
    And User re-enters last name as <InvalidLastName>
    When User clicks on continue
    Examples:
      | PassportSubject             | InvalidLastName |
      | PassportSubjectHappyKenneth | KYLE123         |


  @build @staging @integration @smoke @stub @uat
  Scenario: Beta Banner Reject Analytics
    When I view the Beta banner
    When the beta banner reads This is a new service – your feedback (opens in new tab) will help us to improve it.
    And I select Reject analytics cookies button
    Then I see the Reject Analytics sentence You’ve rejected additional cookies. You can change your cookie settings at any time.
    And  I select the link change your cookie settings
    Then I check the page to change cookie preferences opens
    Then The test is complete and I close the driver

# No longer a valid test as front end form validation prevents the invalid passport number being sent
#  @Passport_test
#  Scenario Outline: Passport details page unhappy path with InvalidPassportDetails
#    Given User enters data as a <PassportSubject>
#    Then User clicks on continue
#    Then I navigate to the passport verifiable issuer to check for a Invalid response
#    And JSON response should contain error description Authorization permission denied and status code as 302
#    And The test is complete and I close the driver
#    Examples:
#      |PassportSubject      |
#      |PassportSubjectUnhappySelina |

  @build @staging @integration @stub @uat
  Scenario Outline: Passport details page unhappy path with IncorrectPassportNumber
    Given User enters data as a <PassportSubject>
    And User re-enters passport number as <InvalidPassportNumber>
    When User clicks on continue
    Then Proper error message for Could not find your details is displayed
    When User clicks on continue
    Then I navigate to the passport verifiable issuer to check for a Valid response
    And JSON payload should contain ci D02, validity score 0 and strength score 4
    And JSON response should contain documentNumber 887766551 same as given passport
    And The test is complete and I close the driver
    Examples:
      | PassportSubject             | InvalidPassportNumber |
      | PassportSubjectHappyKenneth | 887766551             |

  @build @staging @integration @stub @uat
  Scenario Outline: Passport details page unhappy path with IncorrectDateOfBirth
    Given User enters data as a <PassportSubject>
    And User re-enters birth day as <InvalidBirthDay>
    And User re-enters birth month as <InvalidBirthMonth>
    And User re-enters birth year as <InvalidBirthYear>
    When User clicks on continue
    Then Proper error message for Could not find your details is displayed
    When User clicks on continue
    Then I navigate to the passport verifiable issuer to check for a Valid response
    And JSON payload should contain ci D02, validity score 0 and strength score 4
    And The test is complete and I close the driver
    Examples:
      | PassportSubject             | InvalidBirthDay | InvalidBirthMonth | InvalidBirthYear |
      | PassportSubjectHappyKenneth | 12              | 08                | 1985             |

  @build @staging @integration @stub @uat
  Scenario Outline: Passport details page unhappy path with IncorrectFirstName
    Given User enters data as a <PassportSubject>
    And User re-enters first name as <InvalidFirstName>
    When User clicks on continue
    Then Proper error message for Could not find your details is displayed
    When User clicks on continue
    Then I navigate to the passport verifiable issuer to check for a Valid response
    And JSON payload should contain ci D02, validity score 0 and strength score 4
    And The test is complete and I close the driver
    Examples:
      | PassportSubject             | InvalidFirstName |
      | PassportSubjectHappyKenneth | SELINA           |

  @build @staging @integration @stub @uat
  Scenario Outline: Passport details page unhappy path with IncorrectLastName
    Given User enters data as a <PassportSubject>
    And User re-enters last name as <InvalidLastName>
    When User clicks on continue
    Then Proper error message for Could not find your details is displayed
    When User clicks on continue
    Then I navigate to the passport verifiable issuer to check for a Valid response
    And JSON payload should contain ci D02, validity score 0 and strength score 4
    And The test is complete and I close the driver
    Examples:
      | PassportSubject             | InvalidLastName |
      | PassportSubjectHappyKenneth | KYLE            |

# # Invalid test - expiry date not checked in DCS stub
#  @Passport_test @build @staging @integration
#  Scenario Outline: Passport details page unhappy path with IncorrectValidToDate
#    Given User enters data as a <PassportSubject>
#    When User clicks on continue
#    Then Proper error message for Could not find your details is displayed
#    When User clicks on continue
#    Then I navigate to the Passport verifiable issuer to check for a Valid response
#    And JSON payload should contain ci D02, validity score 0 and strength score 3
#    And The test is complete and I close the driver
#    Examples:
#      |PassportSubject |
#      |IncorrectValidToDate|

  @build @staging @integration @smoke @stub @uat
  Scenario Outline: Passport Retry Test Happy Path
    Given User enters invalid passport details
    When User clicks on continue
    Then Proper error message for Could not find your details is displayed
    When User Re-enters data as a <PassportSubject>
    And User clicks on continue
    Then I navigate to the passport verifiable issuer to check for a Valid response
    And JSON payload should contain validity score 2 and strength score 4
    And The test is complete and I close the driver
    Examples:
      | PassportSubject             |
      | PassportSubjectHappyKenneth |

  @build @staging @integration @smoke @stub @uat
  Scenario Outline: Passport User failed second attempt
    Given User enters invalid passport details
    When User clicks on continue
    Then Proper error message for Could not find your details is displayed
    When User Re-enters data as a <PassportSubject>
    And User re-enters passport number as <InvalidPassportNumber>
    And User clicks on continue
    Then I navigate to the passport verifiable issuer to check for a Valid response
    And JSON payload should contain ci D02, validity score 0 and strength score 4
    And The test is complete and I close the driver
    Examples:
      | PassportSubject             | InvalidPassportNumber |
      | PassportSubjectHappyKenneth | 887766551             |

  @build @staging @integration @smoke @stub @uat
  Scenario: Passport User cancels after failed first attempt
    Given User enters invalid passport details
    When User clicks on continue
    Then Proper error message for Could not find your details is displayed
    When User click on ‘prove your identity another way' Link
    Then I navigate to the passport verifiable issuer to check for a Valid response
    And JSON payload should contain ci D02, validity score 0 and strength score 4
    And The test is complete and I close the driver

  @smoke
  Scenario: Passport User cancels before first attempt via prove your identity another way route
    Given User click on ‘prove your identity another way' Link
    Then I navigate to the passport verifiable issuer to check for a Invalid response
    And JSON response should contain error description Authorization permission denied and status code as 302
    And The test is complete and I close the driver

###########   Field Validations ##########
  @build @staging @integration @smoke @stub @uat
  Scenario Outline: Passport Generate VC with invalid Passport number and prove in another way unhappy path
    Given User enters data as a <PassportSubject>
    And User re-enters passport number as <InvalidPassportNumber>
    When User clicks on continue
    When User click on ‘prove your identity another way' Link
    Then I navigate to the passport verifiable issuer to check for a Valid response
    And JSON response should contain documentNumber 887766551 same as given passport
    And The test is complete and I close the driver
    Examples:
      | PassportSubject             | InvalidPassportNumber |
      | PassportSubjectHappyKenneth | 887766551             |

  @build @staging @integration @smoke @stub @uat
  Scenario Outline: Passport expiry date valid
    Given User enters data as a <PassportSubject>
    Then User enters expiry date as current date minus <months> months and minus <daysToSubtract> days
    When User clicks on continue
    Then I navigate to the passport verifiable issuer to check for a Valid response
    And JSON payload should contain validity score 2 and strength score 4
    And JSON response should contain documentNumber 321654987 same as given passport
#    And exp should be absent in the JSON payload
    And The test is complete and I close the driver
    Examples:
      | PassportSubject             | months | daysToSubtract |
      | PassportSubjectHappyKenneth | 18     | 0              |

  @build @staging @integration @smoke @stub @uat
  Scenario Outline: Passport expiry date invalid
    Given User enters data as a <PassportSubject>
    Then User enters expiry date as current date minus <months> months and minus <daysToSubtract> days
    When User clicks on continue
    Then I can see the valid to date error in the error summary as Your passport must not have expired more than 18 months ago
    And I can see the Valid to date field error as Error:Your passport must not have expired more than 18 months ago
    And The test is complete and I close the driver

    Examples:
      | PassportSubject             | months | daysToSubtract |
      | PassportSubjectHappyKenneth | 18     | 1              |
      | PassportSubjectHappyKenneth | 18     | 2              |

  @build @staging @integration @stub @uat
  Scenario: Check the Unrecoverable error/ Unknown error in Passport CRI
    Given I delete the service_session cookie to get the unexpected error
    When I check the page title is Sorry, there is a problem – GOV.UK One Login
    And The test is complete and I close the driver

  @build @Language-regression
  Scenario Outline: Language Title validation
    Given User clicks on language toggle and switches to Welsh
    Then I check the page title is Rhowch eich manylion yn union fel maent yn ymddangos ar eich pasbort y DU – GOV.UK One Login
    Then User enters data as a <PassportSubject>
    When User clicks on continue
    Then I navigate to the passport verifiable issuer to check for a Valid response
    And JSON payload should contain validity score 2 and strength score 4
    And JSON response should contain documentNumber 321654987 same as given passport
    And Passport VC should contain JTI field
    And exp should be absent in the JSON payload
    And The test is complete and I close the driver
    Examples:
      | PassportSubject             |
      | PassportSubjectHappyKenneth |

  @build @stub
  Scenario Outline: Error tab title validation
    And I check the page title is Enter your details exactly as they appear on your UK passport – GOV.UK One Login
    Then User enters data as a <PassportSubject>
    And User re-enters passport number as <InvalidPassportNumber>
    And User re-enters last name as <InvalidLastName>
    And User re-enters first name as <InvalidFirstName>
    And User re-enters birth day as <InvalidBirthDay>
    And User re-enters birth month as <InvalidBirthMonth>
    And User re-enters birth year as <InvalidBirthYear>
    And User re-enters valid to day as <InvalidValidToDay>
    And User re-enters valid to month as <InvalidValidToMonth>
    And User re-enters valid to year as <InvalidValidToYear>
    And User clicks on continue
    Then I check the page title is Error: Enter your details exactly as they appear on your UK passport – GOV.UK One Login
    And The test is complete and I close the driver
    Examples:
      | PassportSubject             | InvalidPassportNumber | InvalidLastName | InvalidFirstName | InvalidBirthDay | InvalidBirthMonth | InvalidBirthYear | InvalidValidToDay | InvalidValidToMonth | InvalidValidToYear | Scenario                              |
      | PassportSubjectHappyKenneth | 321654987             |                 | KENNETH          | 08              | 07                | 1965             | 01                | 10                  | 2042               | NoLastName                            |
      | PassportSubjectHappyKenneth | 321654987             | DECERQUEIRA     |                  | 08              | 07                | 1965             | 01                | 10                  | 2042               | NoFirstName                           |
      | PassportSubjectHappyKenneth | 321654987             | DECERQUEIRA     | KENNETH          |                 |                   |                  | 01                | 10                  | 2042               | NoDateOfBirth                         |
      | PassportSubjectHappyKenneth | 321654987             | DECERQUEIRA     | KENNETH          | 08              | 07                | 1965             |                   |                     |                    | NoValidToDate                         |
      | PassportSubjectHappyKenneth |                       | DECERQUEIRA     | KENNETH          | 08              | 07                | 1965             | 01                | 10                  | 2042               | NoPassportNumber                      |
      | PassportSubjectHappyKenneth | 321654987             | DECERQUEIRA     | SELINA987        | 08              | 07                | 1965             | 01                | 10                  | 2042               | InvalidFirstNameWithNumbers           |
      | PassportSubjectHappyKenneth | 321654987             | DECERQUEIRA     | SELINA%$@        | 08              | 07                | 1965             | 01                | 10                  | 2042               | InvalidFirstNameWithSpecialCharacters |
      | PassportSubjectHappyKenneth | 321654987             | DECERQUEIRA     | KENNETH          | @               | *&                | 19 7             | 01                | 10                  | 2042               | DateOfBirthWithSpecialCharacters      |
      | PassportSubjectHappyKenneth | 321654987             | DECERQUEIRA     | KENNETH          | 51              | 71                | 198              | 01                | 10                  | 2042               | InvalidDateOfBirth                    |
      | PassportSubjectHappyKenneth | 321654987             | DECERQUEIRA     | KENNETH          | 10              | 10                | 2042             | 01                | 10                  | 2042               | DateOfBirthInFuture                   |
      | PassportSubjectHappyKenneth | 321654987             | DECERQUEIRA     | KENNETH          | 08              | 07                | 1965             | !@                | £$                  | %^ *               | ValidToDateWithSpecialCharacters      |
      | PassportSubjectHappyKenneth | 321654987             | DECERQUEIRA     | KENNETH          | 08              | 07                | 1965             | 10                | 01                  | 2010               | ValidToDateInPast                     |
      | PassportSubjectHappyKenneth | 555667^&*             | DECERQUEIRA     | KENNETH          | 08              | 07                | 1965             | 01                | 10                  | 2042               | PassportNumberWithSpecialChar         |
