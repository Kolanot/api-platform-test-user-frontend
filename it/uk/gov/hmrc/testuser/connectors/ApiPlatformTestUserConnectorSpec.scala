/*
 * Copyright 2018 HM Revenue & Customs
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package uk.gov.hmrc.testuser.connectors

import com.github.tomakehurst.wiremock.client.WireMock._
import play.api.{Configuration, Environment}
import play.api.http.Status._
import play.api.libs.json.Json
import uk.gov.hmrc.http.HeaderCarrier
import uk.gov.hmrc.play.bootstrap.config.ServicesConfig
import uk.gov.hmrc.play.test.{UnitSpec, WithFakeApplication}
import uk.gov.hmrc.testuser.models._
import uk.gov.hmrc.testuser.models.JsonFormatters._
import uk.gov.hmrc.testuser.wiring.AppConfig


class ApiPlatformTestUserConnectorSpec extends UnitSpec with WiremockSugar with WithFakeApplication {

  private val individualUserId = "individual"
  private val individualPassword = "pwd"
  private val individualSaUtr = "1555369052"
  private val individualNino = "CC333333C"
  private val individualVrn = "999902541"

  private val jsonTestIndividual = s"""
      |{
      |  "userId":"$individualUserId",
      |  "password":"$individualPassword",
      |  "saUtr":"$individualSaUtr",
      |  "nino":"$individualNino",
      |  "vrn":"$individualVrn"
      |}
    """.stripMargin

  trait Setup {
    implicit val hc = HeaderCarrier()

    val underTest = new ApiPlatformTestUserConnector(
      fakeApplication.injector.instanceOf[ProxiedHttpClient],
      fakeApplication.injector.instanceOf[AppConfig],
      fakeApplication.injector.instanceOf[Configuration],
      fakeApplication.injector.instanceOf[Environment],
      fakeApplication.injector.instanceOf[ServicesConfig]
    ) {
      override val serviceUrl: String = wireMockUrl
    }
  }

  "createIndividual" should {
    "return a generated individual" in new Setup {
      val requestPayload = """{ "serviceNames": [ "national-insurance", "self-assessment", "mtd-income-tax" ] }"""

      stubFor(post(urlEqualTo("/individuals")).withRequestBody(equalToJson(requestPayload))
        .willReturn(aResponse().withStatus(CREATED).withBody(jsonTestIndividual)))

      val result = await(underTest.createIndividual(Seq("national-insurance", "self-assessment", "mtd-income-tax")))

      result.userId shouldBe individualUserId
      result.password shouldBe individualPassword
      result.fields should contain(Field("saUtr", "Self Assessment UTR", individualSaUtr))
      result.fields should contain(Field("nino", "National Insurance Number (NINO)", individualNino))
    }

    "fail when api-platform-test-user returns a response that is not 201 CREATED" in new Setup {

      stubFor(post(urlEqualTo("/individuals")).willReturn(aResponse().withStatus(OK)))

      intercept[RuntimeException](await(underTest.createIndividual(Seq( "national-insurance", "self-assessment", "mtd-income-tax"))))
    }
  }

  "createOrganisation" should {
    "return a generated organisation" in new Setup {
      private val saUtr = "1555369052"
      private val empRef = "555/EIA000"
      private val organisationFields = Seq(
        Field("saUtr", "Self Assessment UTR", saUtr),
        Field("empRef", "Employer Reference", empRef),
        Field("ctUtr", "Corporation Tax UTR", "1555369053"),
        Field("vrn", "VAT Registration Number", "999902541"))
      private val userId = "user"
      private val password = "password"
      val testOrganisation = TestOrganisation(userId, password, organisationFields)

      val requestPayload =
        s"""{
           |  "serviceNames": [
           |    "national-insurance",
           |    "self-assessment",
           |    "mtd-income-tax"
           |  ]
           |}""".stripMargin

      stubFor(post(urlEqualTo("/organisations")).withRequestBody(equalToJson(requestPayload))
        .willReturn(aResponse().withStatus(CREATED).withBody(s"""
          |{
          |  "userId":"$userId",
          |  "password":"$password",
          |  "individualDetails": {
          |    "firstName": "Ida",
          |    "lastName": "Newton",
          |    "dateOfBirth": "1960-06-01",
          |    "address": {
          |      "line1": "45 Springfield Rise",
          |      "line2": "Glasgow",
          |      "postcode": "TS1 1PA"
          |    }
          |  },
          |  "saUtr":"1555369052",
          |  "empRef":"555/EIA000",
          |  "ctUtr":"1555369053",
          |  "vrn":"999902541"
          |}""".stripMargin)))

      val result = await(underTest.createOrganisation(Seq("national-insurance", "self-assessment", "mtd-income-tax")))

      result.userId shouldBe userId
      result.password shouldBe password
      result.fields should contain(Field("saUtr", "Self Assessment UTR", saUtr))
      result.fields should contain(Field("empRef", "Employer Reference", empRef))
    }

    "fail when api-platform-test-user returns a response that is not 201 CREATED" in new Setup {

      stubFor(post(urlEqualTo("/organisations")).willReturn(aResponse().withStatus(OK)))

      intercept[RuntimeException](await(underTest.createOrganisation(Seq("national-insurance", "self-assessment", "mtd-income-tax"))))
    }
  }

  "getServices" when {
    "api-platform-test-user returns a 200 OK response" should {
      "return the services from api-platform-test-user" in new Setup {
        val services = Seq(Service("service-1", "Service One", Seq(UserTypes.INDIVIDUAL)))
        stubFor(get(urlEqualTo("/services")).willReturn(aResponse().withBody(Json.toJson(services).toString()).withStatus(OK)))

        val result = await(underTest.getServices())

        result shouldBe services
      }
    }

    "api-platform-test-user returns a response other than 200 OK" should {
      "throw runtime exception" in new Setup {
        stubFor(get(urlEqualTo("/services")).willReturn(aResponse().withStatus(CREATED)))

        intercept[RuntimeException](await(underTest.getServices()))
      }
    }
  }
}