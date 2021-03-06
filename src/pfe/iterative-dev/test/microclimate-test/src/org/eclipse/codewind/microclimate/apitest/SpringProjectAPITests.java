package org.eclipse.codewind.microclimate.apitest;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;
import static org.junit.Assert.fail;
import static org.junit.Assume.assumeTrue;

import java.net.HttpURLConnection;
import java.util.Date;

import javax.json.JsonArray;
import javax.json.JsonObject;
import javax.json.JsonValue;

import org.eclipse.codewind.microclimate.test.util.AbstractMicroclimateTest;
import org.eclipse.codewind.microclimate.test.util.HttpResponse;
import org.eclipse.codewind.microclimate.test.util.Logger;
import org.eclipse.codewind.microclimate.test.util.MicroclimateTestUtils;
import org.eclipse.codewind.microclimate.test.util.RetryRule;
import org.eclipse.codewind.microclimate.test.util.MicroclimateTestUtils.PROJECT_TYPES;
import org.eclipse.codewind.microclimate.test.util.MicroclimateTestUtils.SUITE_TYPES;
import org.eclipse.codewind.microclimate.test.util.SocketUtil.SocketEvent;
import org.json.JSONArray;
import org.json.JSONObject;

import org.junit.Before;
import org.junit.FixMethodOrder;
import org.junit.Rule;
import org.junit.Test;
import org.junit.runners.MethodSorters;

@FixMethodOrder(MethodSorters.NAME_ASCENDING)
public class SpringProjectAPITests extends AbstractMicroclimateTest {

	public static String exposedPort;
	public static String projectName = "spring" + SUITE_TYPES.apitest + (new Date().getTime());
	private static String testType = System.getProperty("testType");
	private static PROJECT_TYPES projectType = PROJECT_TYPES.spring;

	final String PORT = MicroclimateTestUtils.getPort();
	final String PROTOCOL = MicroclimateTestUtils.getProtocol();

	final String PROJECTS_API = MicroclimateTestUtils.getProjectsAPI();
	final String TYPES_API = MicroclimateTestUtils.getTypesAPI();
	final String STATUS_API = MicroclimateTestUtils.getStatusAPI();
	final String ACTION_API = MicroclimateTestUtils.getActionAPI();

	private static String lastbuild;

    @Rule
    public RetryRule retry = new RetryRule(MicroclimateTestUtils.retryCount);

	@Before
	public void checkTestType() {
		assumeTrue("-DtestType parameter must be set", "local".equalsIgnoreCase(testType));
	}

	@Test(timeout=60000) //60 seconds timeout
	public void TestA001create() {
		String urlParameters  ="{\"name\": \"" + projectName + "\",\"language\": \"java\",\"framework\": \"spring\"}";

		try {
			int httpResult = MicroclimateTestUtils.projectCreation(urlParameters, testType);
			Logger.println(SpringProjectAPITests.class, "TestA001create()", "HttpResult is: " + httpResult);
			assertEquals(HttpURLConnection.HTTP_ACCEPTED, httpResult);
		}
		catch( Exception e ) {
			Logger.println(SpringProjectAPITests.class, "TestA001create()", "Exception occurred during project creation: " + e.getMessage(),e);
			fail("Exception occurred during project creation.");
		}

		return;
	}

	@Test(timeout=30000) //30 seconds timeout
	public void TestA002checkForProject() {
		try {
			while ( true ) {
				if (MicroclimateTestUtils.checkProjectExistency(projectName, testType))
					return;
				else
					Thread.sleep(3000);
			}
		}
		catch( Exception e ) {
			Logger.println(SpringProjectAPITests.class, "TestA002checkForProject()", "Exception occurred when looking for project in projectList: " + e.getMessage(),e);
			fail("Exception occurred when looking for project in projectList");
		}
	}

	@Test(timeout=300000) //5 mins timeout
	public void TestA003checkForContainer() {
		try {
			exposedPort = MicroclimateTestUtils.getexposedPort(projectName, testType, projectType);
			Logger.println(SpringProjectAPITests.class, "TestA003checkForContainer()", "Exposed Port is " + exposedPort);
			assertNotNull("exposedPort for project " + projectName +" is null", exposedPort);
		}
		catch( Exception e ) {
			Logger.println(SpringProjectAPITests.class, "TestA003checkForContainer()", "Exception occurred when looking for exposedport: " + e.getMessage(),e);
			fail("Exception occurred when looking for exposedport");
		}

		return;
	}

	@Test(timeout=180000) //3 mins timeout
	public void TestA005checkEndpoint() {
		assertNotNull("exposedPort for project " + projectName +" is null", exposedPort);
		String expectedString = "You are currently running a Spring server";
		String api = "/";

		try {
			while( true ) {
				if ( MicroclimateTestUtils.checkEndpoint(expectedString, exposedPort, api, testType) )
					return;
				else
					Thread.sleep(3000);
			}
		}
		catch( Exception e ) {
			Logger.println(SpringProjectAPITests.class, "TestA005checkEndpoint()", "Exception occurred when checking for endpoint",e);
			fail("Exception occurred when checking for endpoint");
		}
	}

	@Test(timeout=420000) //7 minutes timeout
	public void TestB001buildAndRun() {
		final String projectCreationEvent = "projectCreation";

		try {
			String projectID = MicroclimateTestUtils.getProjectID(projectName, testType);
			String urlParameters = "{\"projectID\": \"" + projectID + "\",\"projectType\": \"spring\",\"location\": \"/codewind-workspace/" + projectName + "\"}";
			String[] eventsOfInterest = {projectCreationEvent};
			MicroclimateTestUtils.PairedResponse pairedResponse = MicroclimateTestUtils.callAPIBodyParametersWSocketResponse(PROJECTS_API, urlParameters, PROTOCOL, PORT, "POST", testType, eventsOfInterest, 300);
			int httpResult = pairedResponse.httpResponse.getResponseCode();

			Logger.println(SpringProjectAPITests.class, "TestB001buildAndRun()", "HttpResult is: " + httpResult);

			assertEquals(HttpURLConnection.HTTP_ACCEPTED, httpResult);

			JsonObject responseBody = pairedResponse.httpResponse.getResponseBodyAsJsonObject();
			assertNotNull(responseBody.getString("operationId"));

			assertEquals("Expecting only 1 socket event", 1, pairedResponse.socketEvents.length);
			SocketEvent se = pairedResponse.socketEvents[0];
			Logger.println(SpringProjectAPITests.class, "TestB001buildAndRun()", "Socket msg: " + se.getMsg());
			assertEquals("Unexpected socket event received", projectCreationEvent, se.getMsg());
			Logger.println(SpringProjectAPITests.class, "TestB001buildAndRun()", "Socket event details: " + se.getDetails().toString());

			JSONObject socketResponseBody = se.getDetails();
			assertNotNull(socketResponseBody);
			assertNotNull(socketResponseBody.getString("operationId"));
			assertNotNull(socketResponseBody.getString("projectID"));
			assertEquals(projectID, socketResponseBody.getString("projectID"));
			assertNotNull(socketResponseBody.getString("host"));
			assertNotNull(socketResponseBody.getJSONObject("ports"));
			assertNotNull(socketResponseBody.getJSONObject("ports").getString("exposedPort"));
			assertNotNull(socketResponseBody.getJSONObject("ports").getString("internalPort"));
			assertEquals("success", socketResponseBody.getString("status"));
			assertNotNull(socketResponseBody.getJSONObject("logs"));

			// Wait for the start project to finish
			long timeout = 90000;
			boolean isStart = MicroclimateTestUtils.waitForProjectStarted(projectID, testType, timeout);
			assertTrue("Project " + projectName + " did not start within " + timeout + "ms", isStart);
		}
		catch( Exception e ) {
			Logger.println(SpringProjectAPITests.class, "TestB001buildAndRun()", "Exception occurred during project build & run: " + e.getMessage(),e);
			fail("Exception occurred during project build & run");
		}

		return;
	}

	@Test(timeout=420000) //7 minutes timeout
	public void TestB002buildAndRunStartModeRun() {
		final String projectCreationEvent = "projectCreation";

		try {
			String projectID = MicroclimateTestUtils.getProjectID(projectName, testType);
			String urlParameters = "{\"projectID\": \"" + projectID + "\",\"projectType\": \"spring\",\"location\": \"/codewind-workspace/" + projectName + "\",\"startMode\": \"run\"}";
			String[] eventsOfInterest = {projectCreationEvent};
			MicroclimateTestUtils.PairedResponse pairedResponse = MicroclimateTestUtils.callAPIBodyParametersWSocketResponse(PROJECTS_API, urlParameters, PROTOCOL, PORT, "POST", testType, eventsOfInterest, 300);
			int httpResult = pairedResponse.httpResponse.getResponseCode();

			Logger.println(SpringProjectAPITests.class, "TestB002buildAndRunStartModeRun()", "HttpResult is: " + httpResult);

			assertEquals(HttpURLConnection.HTTP_ACCEPTED, httpResult);

			JsonObject responseBody = pairedResponse.httpResponse.getResponseBodyAsJsonObject();
			assertNotNull(responseBody.getString("operationId"));

			assertEquals("Expecting only 1 socket event", 1, pairedResponse.socketEvents.length);
			SocketEvent se = pairedResponse.socketEvents[0];
			Logger.println(SpringProjectAPITests.class, "TestB002buildAndRunStartModeRun()", "Socket msg: " + se.getMsg());
			assertEquals("Unexpected socket event received", projectCreationEvent, se.getMsg());
			Logger.println(SpringProjectAPITests.class, "TestB002buildAndRunStartModeRun()", "Socket event details: " + se.getDetails().toString());

			JSONObject socketResponseBody = se.getDetails();
			assertNotNull(socketResponseBody);
			assertNotNull(socketResponseBody.getString("operationId"));
			assertNotNull(socketResponseBody.getString("projectID"));
			assertEquals(projectID, socketResponseBody.getString("projectID"));
			assertNotNull(socketResponseBody.getString("host"));
			assertNotNull(socketResponseBody.getJSONObject("ports"));
			assertNotNull(socketResponseBody.getJSONObject("ports").getString("exposedPort"));
			assertNotNull(socketResponseBody.getJSONObject("ports").getString("internalPort"));
			assertTrue(!socketResponseBody.getJSONObject("ports").has("exposedDebugPort"));
			assertTrue(!socketResponseBody.getJSONObject("ports").has("internalDebugPort"));
			assertEquals("success", socketResponseBody.getString("status"));
			assertNotNull(socketResponseBody.getJSONObject("logs"));

			// Wait for the start project to finish
			long timeout = 90000;
			boolean isStart = MicroclimateTestUtils.waitForProjectStarted(projectID, testType, timeout);
			assertTrue("Project " + projectName + " did not start within " + timeout + "ms", isStart);
		}
		catch( Exception e ) {
			Logger.println(SpringProjectAPITests.class, "TestB002buildAndRunStartModeRun()", "Exception occurred during project build & run: " + e.getMessage(),e);
			fail("Exception occurred during project build & run");
		}
	}

	@Test(timeout=30000) //30 seconds timeout
	public void TestC001getProjectTypes() {
		try {
			String api = PROJECTS_API + "types?location=/codewind-workspace/" + projectName;
			String url = MicroclimateTestUtils.getBaseURL(testType, PORT, PROTOCOL) + api;

			HttpResponse httpResponse = MicroclimateTestUtils.callAPIURLParameters(url, "GET", testType);
			int httpResult = httpResponse.getResponseCode();

			Logger.println(SpringProjectAPITests.class, "TestC001getProjectTypes()", "HttpResult is: " + httpResult);
			assertEquals(HttpURLConnection.HTTP_OK, httpResult);

			JsonObject jsonObject = httpResponse.getResponseBodyAsJsonObject();
			assertEquals("spring", jsonObject.getJsonArray("types").getString(0));
		}
		catch( Exception e ) {
			Logger.println(SpringProjectAPITests.class, "TestC001getProjectTypes()", "Exception occurred during get project types: " + e.getMessage(),e);
			fail("Exception occurred during project get types");
		}
	}

	@Test(timeout=30000) //30 seconds timeout
	public void TestD001getProjectLogs() {
		try {
			String projectID = MicroclimateTestUtils.getProjectID(projectName, testType);
			String api = PROJECTS_API + projectID + "/logs";
			String url = MicroclimateTestUtils.getBaseURL(testType, PORT, PROTOCOL) + api;

			HttpResponse httpResponse = MicroclimateTestUtils.callAPIURLParameters(url, "GET", testType);
			int httpResult = httpResponse.getResponseCode();

			Logger.println(SpringProjectAPITests.class, "TestD001getProjectLogs()", "HttpResult is: " + httpResult);
			assertEquals(HttpURLConnection.HTTP_OK, httpResult);

		    JsonObject jsonObject = httpResponse.getResponseBodyAsJsonObject();

		    JsonObject build = jsonObject.getJsonObject("build");
		    assertNotNull(build);
		    String buildOrigin = build.getString("origin");
		    assertNotNull(buildOrigin);
		    assertTrue(buildOrigin.equals("workspace") || buildOrigin.equals("container"));
		    JsonArray buildFiles = build.getJsonArray("files");
		    assertNotNull(buildFiles);
		    for(JsonValue arrayVals : buildFiles){
		    	String filename = arrayVals.toString();
		    	assertTrue(MicroclimateTestUtils.checkFileExistsInContainer(filename, buildOrigin.equals("workspace") ? "codewind-pfe" : projectName, testType));
	        }
		}
		catch( Exception e ) {
			Logger.println(SpringProjectAPITests.class, "TestD001getProjectLogs()", "Exception occurred during get project logs: " + e.getMessage(),e);
			fail("Exception occurred during project get logs");
		}
	}

	@Test(timeout=30000) //30 seconds timeout
	public void TestF001actionDisableAutobuild() {
		try {
			String projectID = MicroclimateTestUtils.getProjectID(projectName, testType);
			String urlParameters = "{\"action\": \"disableautobuild\",\"projectID\": \"" + projectID + "\"}";
			HttpResponse httpResponse = MicroclimateTestUtils.callAPIBodyParameters(ACTION_API, urlParameters, PROTOCOL, PORT, "POST", testType);
			int httpResult = httpResponse.getResponseCode();

			Logger.println(SpringProjectAPITests.class, "TestF001actionDisableAutobuild()", "HttpResult is: " + httpResult);
			assertEquals(HttpURLConnection.HTTP_OK, httpResult);

			JsonObject responseBody = httpResponse.getResponseBodyAsJsonObject();
			assertEquals("success", responseBody.getString("status"));
		}
		catch( Exception e ) {
			Logger.println(SpringProjectAPITests.class, "TestF001actionDisableAutobuild()", "Exception occurred during project action: " + e.getMessage(),e);
			fail("Exception occurred during project action");
		}

		return;
	}

	@Test(timeout=30000) //30 seconds timeout
	public void TestF002actionEnableAutobuild() {
		try {
			String projectID = MicroclimateTestUtils.getProjectID(projectName, testType);
			String urlParameters = "{\"action\": \"enableautobuild\",\"projectID\": \"" + projectID + "\"}";
			HttpResponse httpResponse = MicroclimateTestUtils.callAPIBodyParameters(ACTION_API, urlParameters, PROTOCOL, PORT, "POST", testType);
			int httpResult = httpResponse.getResponseCode();

			Logger.println(SpringProjectAPITests.class, "TestF002actionEnableAutobuild()", "HttpResult is: " + httpResult);
			assertEquals(HttpURLConnection.HTTP_ACCEPTED, httpResult);

			JsonObject responseBody = httpResponse.getResponseBodyAsJsonObject();
			assertEquals("success", responseBody.getString("status"));
		}
		catch( Exception e ) {
			Logger.println(SpringProjectAPITests.class, "TestF002actionEnableAutobuild()", "Exception occurred during project action: " + e.getMessage(),e);
			fail("Exception occurred during project action");
		}

		return;
	}

	@Test(timeout=420000) //7 minutes timeout
	public void TestG001actionBuild() {
		final String projectChangedEvent = "projectChanged";
		final String projectStatusChangedEvent = "projectStatusChanged";
		try {
			String projectID = MicroclimateTestUtils.getProjectID(projectName, testType);
			String urlParameters = "{\"action\": \"build\",\"projectType\": \"spring\", \"projectID\": \"" + projectID + "\"}";
			String[] eventsOfInterest = {projectChangedEvent,projectStatusChangedEvent};
			MicroclimateTestUtils.PairedResponse pairedResponse = MicroclimateTestUtils.callAPIBodyParametersWSocketResponse(ACTION_API, urlParameters, PROTOCOL, PORT, "POST", testType, eventsOfInterest, 300);
			int httpResult = pairedResponse.httpResponse.getResponseCode();

			Logger.println(SpringProjectAPITests.class, "TestG001actionBuild()", "HttpResult is: " + httpResult);
			assertEquals(HttpURLConnection.HTTP_ACCEPTED, httpResult);

			JsonObject responseBody = pairedResponse.httpResponse.getResponseBodyAsJsonObject();
			assertNotNull(responseBody.getString("operationId"));

			assertEquals("Expecting only 1 socket event", 1, pairedResponse.socketEvents.length);
			SocketEvent se = pairedResponse.socketEvents[0];
			Logger.println(SpringProjectAPITests.class, "TestG001actionBuild()", "Socket msg: " + se.getMsg());
			assertEquals("Unexpected socket event received", projectChangedEvent, se.getMsg());
			Logger.println(SpringProjectAPITests.class, "TestG001actionBuild()", "Socket event details: " + se.getDetails().toString());

			JSONObject socketResponseBody = se.getDetails();
			assertNotNull(socketResponseBody);
			assertEquals(projectID, socketResponseBody.getString("projectID"));
			assertEquals("success", socketResponseBody.getString("status"));
			// Wait for the start project to finish
			long timeout = 90000;
			boolean isStart = MicroclimateTestUtils.waitForProjectStarted(projectID, testType, timeout);
			assertTrue("Project " + projectName + " did not start within " + timeout + "ms", isStart);
		}
		catch( Exception e ) {
			Logger.println(SpringProjectAPITests.class, "TestG001actionBuild()", "Exception occurred during project action: " + e.getMessage(),e);
			fail("Exception occurred during project action");
		}

		return;
	}

	@Test(timeout=60000) //1 min timeout
	public void TestG003checkLastbuildTimestamp() {
		try {
			String projectID = MicroclimateTestUtils.getProjectID(projectName, testType);
			SocketEvent[] statusChangedEvent = MicroclimateTestUtils.getProjectStatusChangedEvents(projectID);
			for(SocketEvent event : statusChangedEvent) {
				JSONObject socketResponseBody = event.getDetails();
				assertNotNull(socketResponseBody);
				assertEquals(projectID, socketResponseBody.getString("projectID"));
				if(socketResponseBody.has("buildStatus") && (socketResponseBody.getString("buildStatus").equals("success") || socketResponseBody.getString("buildStatus").equals("failed"))) {
					assertNotNull(socketResponseBody.getString("lastbuild"));
					lastbuild = socketResponseBody.getString("lastbuild");
					Logger.println(SpringProjectAPITests.class, "TestG003checkLastbuildTimestamp()", "lastbuild timestamp is: " + lastbuild);
					break;
				}
			}
		}
		catch( Exception e ) {
			Logger.println(SpringProjectAPITests.class, "TestG003checkLastbuildTimestamp()", "Exception occurred when checking for lastbuild timestamp: " + e.getMessage(),e);
			fail("Exception occurred when checking for lastbuild timestamp");
		}
	}

	@Test(timeout=60000) //60 seconds timeout
	public void TestG004actionBuildInvalidProjectType() {
		try {
			String urlParameters = "{\"action\": \"build\",\"projectType\": \"liberty\", \"projectID\": \"" + projectName + "\"}";
			HttpResponse httpResponse = MicroclimateTestUtils.callAPIBodyParameters(ACTION_API, urlParameters, PROTOCOL, PORT, "POST", testType);
			int httpResult = httpResponse.getResponseCode();

			Logger.println(SpringProjectAPITests.class, "TestG004actionBuildInvalidProjectType()", "HttpResult is: " + httpResult);
			assertEquals(HttpURLConnection.HTTP_INTERNAL_ERROR, httpResult);
		}
		catch( Exception e ) {
			Logger.println(SpringProjectAPITests.class, "TestG004actionBuildInvalidProjectID()", "Exception occurred during project action: " + e.getMessage(),e);
			fail("Exception occurred during project action");
		}

		return;
	}

	@Test(timeout=30000) //30 seconds timeout
	public void TestH001getProjectStatusApp() {
		try {
			// for spring projects, we need to wait for the build to be completed and the app start, so sleep for 10s
			Thread.sleep(10000);

			String projectID = MicroclimateTestUtils.getProjectID(projectName, testType);
			String api = STATUS_API + "?type=appState&projectID=" + projectID;
			String url = MicroclimateTestUtils.getBaseURL(testType, PORT, PROTOCOL) + api;

			HttpResponse httpResponse = MicroclimateTestUtils.callAPIURLParameters(url, "GET", testType);
			int httpResult = httpResponse.getResponseCode();

			Logger.println(SpringProjectAPITests.class, "TestH001getProjectStatusApp()", "HttpResult is: " + httpResult);
			assertEquals(HttpURLConnection.HTTP_OK, httpResult);

		    JsonObject jsonObject = httpResponse.getResponseBodyAsJsonObject();
		    String appStatus = jsonObject.getString("appStatus");
		    assertNotNull(appStatus);
		    assertTrue(appStatus.equals("starting") || appStatus.equals("started") || appStatus.equals("stopping")
		    								|| appStatus.equals("stopped") || appStatus.equals("unknown"));
		    // note: complete app status testing is done in the smoke tests
		}
		catch( Exception e ) {
			Logger.println(SpringProjectAPITests.class, "TestH001getProjectStatusApp()", "Exception occurred during get project status: " + e.getMessage(),e);
			fail("Exception occurred during project get logs");
		}
	}

	@Test(timeout=30000) //30 seconds timeout
	public void TestH002getProjectStatusBuild() {
		try {
			String projectID = MicroclimateTestUtils.getProjectID(projectName, testType);
			String api = STATUS_API + "?type=buildState&projectID=" + projectID;
			String url = MicroclimateTestUtils.getBaseURL(testType, PORT, PROTOCOL) + api;

			HttpResponse httpResponse = MicroclimateTestUtils.callAPIURLParameters(url, "GET", testType);
			int httpResult = httpResponse.getResponseCode();

			Logger.println(SpringProjectAPITests.class, "TestH002getProjectStatusBuild()", "HttpResult is: " + httpResult);
			assertEquals(HttpURLConnection.HTTP_OK, httpResult);

		    JsonObject jsonObject = httpResponse.getResponseBodyAsJsonObject();
		    String buildStatus = jsonObject.getString("buildStatus");
		    assertNotNull(buildStatus);
		    assertTrue(buildStatus.equals("queued") || buildStatus.equals("inProgress") || buildStatus.equals("success") || buildStatus.equals("failed") || buildStatus.equals("unknown"));
		    assertFalse(jsonObject.getJsonObject("buildRequired").getBoolean("state"));
		    if (!buildStatus.equals("queued"))
		    		assertNotNull(jsonObject.getString("detailedBuildStatus"));
		    if (buildStatus.equals("success") || buildStatus.equals("failed"))
	    			assertEquals(lastbuild, jsonObject.getJsonNumber("lastbuild").toString());
		    // note: complete build status testing is done in the smoke tests
		}
		catch( Exception e ) {
			Logger.println(SpringProjectAPITests.class, "TestH002getProjectStatusBuild()", "Exception occurred during get project status: " + e.getMessage(),e);
			fail("Exception occurred during project get logs");
		}
	}

	@Test(timeout=30000) //30 seconds timeout
	public void TestI001getProjectCapabilities() {
		try {
			String projectID = MicroclimateTestUtils.getProjectID(projectName, testType);
			String api = PROJECTS_API + projectID + "/capabilities";
			String url = MicroclimateTestUtils.getBaseURL(testType, PORT, PROTOCOL) + api;

			HttpResponse httpResponse = MicroclimateTestUtils.callAPIURLParameters(url, "GET", testType);
			int httpResult = httpResponse.getResponseCode();

			Logger.println(SpringProjectAPITests.class, "TestI001getProjectCapabilities()", "HttpResult is: " + httpResult);
			assertEquals(HttpURLConnection.HTTP_OK, httpResult);

			JsonObject capabilities = httpResponse.getResponseBodyAsJsonObject().getJsonObject("capabilities");
			assertNotNull(capabilities);
			JsonArray startModes = capabilities.getJsonArray("startModes");
			assertNotNull(startModes);
			assertTrue(MicroclimateTestUtils.jsonArrayContains(startModes, "\"run\""));
			assertTrue(MicroclimateTestUtils.jsonArrayContains(startModes, "\"debug\""));
			JsonArray controlCommands = capabilities.getJsonArray("controlCommands");
			assertNotNull(controlCommands);
			assertTrue(MicroclimateTestUtils.jsonArrayContains(controlCommands, "\"restart\""));
		}
		catch( Exception e ) {
			Logger.println(SpringProjectAPITests.class, "TestI001getProjectCapabilities()", "Exception occurred during get project types: " + e.getMessage(),e);
			fail("Exception occurred during project get types");
		}
	}

	/*
	@Test(timeout=300000) //5 mins timeout
	public void TestK001changeContextRoot() {
		try {
			final String projectSettingsChangedEvent = "projectSettingsChanged";
			String[] eventsOfInterest = {projectSettingsChangedEvent};
			String projectID = MicroclimateTestUtils.getProjectID(projectName, testType);
			String SettingsAPI = MicroclimateTestUtils.getSettingsAPI(projectID);
			String urlParameters = "{\"settings\": [{\"name\": \"contextRoot\",\"value\": \"contextspring\"}]}";
			MicroclimateTestUtils.PairedResponse pairedResponse = MicroclimateTestUtils.callAPIBodyParametersWSocketResponse(SettingsAPI, urlParameters, PROTOCOL, PORT, "POST", testType, eventsOfInterest, 300);
			int httpResult = pairedResponse.httpResponse.getResponseCode();
			Logger.println(SpringProjectAPITests.class, "TestK001changeContextRoot()", "HttpResult is: " + httpResult);
			assertEquals(HttpURLConnection.HTTP_ACCEPTED, httpResult);

			JsonObject responseBody = pairedResponse.httpResponse.getResponseBodyAsJsonObject();
			assertNotNull(responseBody.getString("operationId"));

			assertEquals("Expecting only 1 socket event", 1, pairedResponse.socketEvents.length);
			SocketEvent se = pairedResponse.socketEvents[0];
			Logger.log("Socket msg: " + se.getMsg());
			assertEquals("Unexpected socket event received", projectSettingsChangedEvent, se.getMsg());
			Logger.log("Socket event details: " + se.getDetails().toString());

			JSONObject socketResponseBody = se.getDetails();
			assertNotNull(socketResponseBody);
			assertNotNull(socketResponseBody.getString("operationId"));
			assertNotNull(socketResponseBody.getString("projectID"));
			assertEquals(projectID, socketResponseBody.getString("projectID"));
			assertEquals("contextRoot", socketResponseBody.getString("name"));
			assertEquals("/contextspring", socketResponseBody.getString("contextRoot"));
			assertEquals("success", socketResponseBody.getString("status"));

			// Wait for the stop project to finish
			long timeout = 60000;
			boolean isStop = MicroclimateTestUtils.waitForProjectStopped(projectID, testType, timeout);
			assertTrue("Project " + projectName + " did not stop within " + timeout + "ms", isStop);

			urlParameters = "{\"settings\": [{\"name\": \"contextRoot\",\"value\": \"/\"}]}";
			pairedResponse = MicroclimateTestUtils.callAPIBodyParametersWSocketResponse(SettingsAPI, urlParameters, PROTOCOL, PORT, "POST", testType, eventsOfInterest, 300);
			httpResult = pairedResponse.httpResponse.getResponseCode();
			Logger.println(SpringProjectAPITests.class, "TestK001changeContextRoot()", "HttpResult is: " + httpResult);
			assertEquals(HttpURLConnection.HTTP_ACCEPTED, httpResult);

			// Wait for the start project to finish
			boolean isStart = MicroclimateTestUtils.waitForProjectStarted(projectID, testType, timeout);
			assertTrue("Project " + projectName + " did not start within " + timeout + "ms", isStart);

			exposedPort = MicroclimateTestUtils.getexposedPort(projectName, testType, projectType);
			assertNotNull("exposedPort for project " + projectName +" is null", exposedPort);
			String expectedString = "You are currently running a Spring server";
			String api = "/";

			try {
				while( true ) {
					if ( MicroclimateTestUtils.checkEndpoint(expectedString, exposedPort, api, testType) ) {
						return;
					} else {
						Thread.sleep(3000);
					}
				}
			}
			catch( Exception e ) {
				Logger.println(SpringProjectAPITests.class, "TestK001changeContextRoot()", "Exception occurred when checking for endpoint after setting context root",e);
				fail("Exception occurred when checking for endpoint after setting context root");
			}
		}
		catch( Exception e ) {
			Logger.println(SpringProjectAPITests.class, "TestK001changeContextRoot()", "Exception occurred when setting context root: " + e.getMessage(),e);
			fail("Exception occurred when setting context root");
		}
	}

	@Test(timeout=30000) //30 seconds timeout
	public void TestK002changeContextRootInvalidContextRoot() {
		try {
			final String projectSettingsChangedEvent = "projectSettingsChanged";
			String[] eventsOfInterest = {projectSettingsChangedEvent};
			String projectID = MicroclimateTestUtils.getProjectID(projectName, testType);
			String SettingsAPI = MicroclimateTestUtils.getSettingsAPI(projectID);
			String urlParameters = "{\"settings\": [{\"name\": \"contextRoot\",\"value\": \"<strong>hello</strong><script>alert(/xss/);</script>end\"}]}";
			MicroclimateTestUtils.PairedResponse pairedResponse = MicroclimateTestUtils.callAPIBodyParametersWSocketResponse(SettingsAPI, urlParameters, PROTOCOL, PORT, "POST", testType, eventsOfInterest, 300);
			int httpResult = pairedResponse.httpResponse.getResponseCode();
			Logger.println(SpringProjectAPITests.class, "TestK002changeContextRootInvalidContextRoot()", "HttpResult is: " + httpResult);
			assertEquals(HttpURLConnection.HTTP_ACCEPTED, httpResult);

			JsonObject responseBody = pairedResponse.httpResponse.getResponseBodyAsJsonObject();
			assertNotNull(responseBody.getString("operationId"));

			assertEquals("Expecting only 1 socket event", 1, pairedResponse.socketEvents.length);
			SocketEvent se = pairedResponse.socketEvents[0];
			Logger.log("Socket msg: " + se.getMsg());
			assertEquals("Unexpected socket event received", projectSettingsChangedEvent, se.getMsg());
			Logger.log("Socket event details: " + se.getDetails().toString());

			JSONObject socketResponseBody = se.getDetails();
			assertNotNull(socketResponseBody);
			assertNotNull(socketResponseBody.getString("operationId"));
			assertNotNull(socketResponseBody.getString("projectID"));
			assertEquals(projectID, socketResponseBody.getString("projectID"));
			assertEquals("failed", socketResponseBody.getString("status"));
			assertEquals("BAD_REQUEST: The context root is not valid", socketResponseBody.getString("error"));
		}
		catch( Exception e ) {
			Logger.println(SpringProjectAPITests.class, "TestK002changeContextRootInvalidContextRoot()", "Exception occurred when setting invlid context root: " + e.getMessage(),e);
			fail("Exception occurred when setting invlid context root");
		}
	}

	@Test(timeout=300000) //5 mins timeout
	public void TestK003changeHealthCheck() {
		try {
			final String projectSettingsChangedEvent = "projectSettingsChanged";
			String[] eventsOfInterest = {projectSettingsChangedEvent};
			String projectID = MicroclimateTestUtils.getProjectID(projectName, testType);
			String SettingsAPI = MicroclimateTestUtils.getSettingsAPI(projectID);
			String urlParameters = "{\"settings\": [{\"name\": \"healthCheck\",\"value\": \"healthspring\"}]}";
			MicroclimateTestUtils.PairedResponse pairedResponse = MicroclimateTestUtils.callAPIBodyParametersWSocketResponse(SettingsAPI, urlParameters, PROTOCOL, PORT, "POST", testType, eventsOfInterest, 300);
			int httpResult = pairedResponse.httpResponse.getResponseCode();
			Logger.println(SpringProjectAPITests.class, "TestK003changeHealthCheck()", "HttpResult is: " + httpResult);
			assertEquals(HttpURLConnection.HTTP_ACCEPTED, httpResult);

			JsonObject responseBody = pairedResponse.httpResponse.getResponseBodyAsJsonObject();
			assertNotNull(responseBody.getString("operationId"));

			assertEquals("Expecting only 1 socket event", 1, pairedResponse.socketEvents.length);
			SocketEvent se = pairedResponse.socketEvents[0];
			Logger.log("Socket msg: " + se.getMsg());
			assertEquals("Unexpected socket event received", projectSettingsChangedEvent, se.getMsg());
			Logger.log("Socket event details: " + se.getDetails().toString());

			JSONObject socketResponseBody = se.getDetails();
			assertNotNull(socketResponseBody);
			assertNotNull(socketResponseBody.getString("operationId"));
			assertNotNull(socketResponseBody.getString("projectID"));
			assertEquals(projectID, socketResponseBody.getString("projectID"));
			assertEquals("healthCheck", socketResponseBody.getString("name"));
			assertEquals("/healthspring", socketResponseBody.getString("healthCheck"));
			assertEquals("success", socketResponseBody.getString("status"));

			// Wait for the stop project to finish
			long timeout = 60000;
			boolean isStop = MicroclimateTestUtils.waitForProjectStopped(projectID, testType, timeout);
			assertTrue("Project " + projectName + " did not stop within " + timeout + "ms", isStop);

			urlParameters = "{\"settings\": [{\"name\": \"healthCheck\",\"value\": \"/\"}]}";
			pairedResponse = MicroclimateTestUtils.callAPIBodyParametersWSocketResponse(SettingsAPI, urlParameters, PROTOCOL, PORT, "POST", testType, eventsOfInterest, 300);
			httpResult = pairedResponse.httpResponse.getResponseCode();
			Logger.println(SpringProjectAPITests.class, "TestK003changeHealthCheck()", "HttpResult is: " + httpResult);
			assertEquals(HttpURLConnection.HTTP_ACCEPTED, httpResult);

			// Wait for the start project to finish
			boolean isStart = MicroclimateTestUtils.waitForProjectStarted(projectID, testType, timeout);
			assertTrue("Project " + projectName + " did not start within " + timeout + "ms", isStart);

			exposedPort = MicroclimateTestUtils.getexposedPort(projectName, testType, projectType);
			assertNotNull("exposedPort for project " + projectName +" is null", exposedPort);
			String expectedString = "You are currently running a Spring server";
			String api = "/";

			try {
				while( true ) {
					if ( MicroclimateTestUtils.checkEndpoint(expectedString, exposedPort, api, testType) ) {
						return;
					} else {
						Thread.sleep(3000);
					}
				}
			}
			catch( Exception e ) {
				Logger.println(SpringProjectAPITests.class, "TestK003changeHealthCheck()", "Exception occurred when checking for endpoint after setting context root",e);
				fail("Exception occurred when checking for endpoint after setting context root");
			}
		}
		catch( Exception e ) {
			Logger.println(SpringProjectAPITests.class, "TestK003changeHealthCheck()", "Exception occurred when setting context root: " + e.getMessage(),e);
			fail("Exception occurred when setting context root");
		}
	}

	@Test(timeout=30000) //30 seconds timeout
	public void TestK004changeHealthCheckInvalidHealthCheck() {
		try {
			final String projectSettingsChangedEvent = "projectSettingsChanged";
			String[] eventsOfInterest = {projectSettingsChangedEvent};
			String projectID = MicroclimateTestUtils.getProjectID(projectName, testType);
			String SettingsAPI = MicroclimateTestUtils.getSettingsAPI(projectID);
			String urlParameters = "{\"settings\": [{\"name\": \"healthCheck\",\"value\": \"<strong>hello</strong><script>alert(/xss/);</script>end\"}]}";
			MicroclimateTestUtils.PairedResponse pairedResponse = MicroclimateTestUtils.callAPIBodyParametersWSocketResponse(SettingsAPI, urlParameters, PROTOCOL, PORT, "POST", testType, eventsOfInterest, 300);
			int httpResult = pairedResponse.httpResponse.getResponseCode();
			Logger.println(SpringProjectAPITests.class, "TestK004changeHealthCheckInvalidHealthCheck()", "HttpResult is: " + httpResult);
			assertEquals(HttpURLConnection.HTTP_ACCEPTED, httpResult);

			JsonObject responseBody = pairedResponse.httpResponse.getResponseBodyAsJsonObject();
			assertNotNull(responseBody.getString("operationId"));

			assertEquals("Expecting only 1 socket event", 1, pairedResponse.socketEvents.length);
			SocketEvent se = pairedResponse.socketEvents[0];
			Logger.log("Socket msg: " + se.getMsg());
			assertEquals("Unexpected socket event received", projectSettingsChangedEvent, se.getMsg());
			Logger.log("Socket event details: " + se.getDetails().toString());

			JSONObject socketResponseBody = se.getDetails();
			assertNotNull(socketResponseBody);
			assertNotNull(socketResponseBody.getString("operationId"));
			assertNotNull(socketResponseBody.getString("projectID"));
			assertEquals(projectID, socketResponseBody.getString("projectID"));
			assertEquals("failed", socketResponseBody.getString("status"));
			assertEquals("BAD_REQUEST: The health check is not valid", socketResponseBody.getString("error"));
		}
		catch( Exception e ) {
			Logger.println(SpringProjectAPITests.class, "TestK004changeHealthCheckInvalidHealthCheck()", "Exception occurred when setting invlid context root: " + e.getMessage(),e);
			fail("Exception occurred when setting invlid context root");
		}
	}
	*/

	@Test(timeout=30000) //30 seconds timeout
	public void TestK005checkProjectSettingsInvalidProjectID() {
		try {
			String projectID = "invalidProjectID";
			String SettingsAPI = MicroclimateTestUtils.getSettingsAPI(projectID);
			String urlParameters = "{\"settings\": {\"internalDebugPort\" : \"7888\"}}";
			HttpResponse httpResponse = MicroclimateTestUtils.callAPIBodyParameters(SettingsAPI, urlParameters, PROTOCOL, PORT, "POST", testType);
			int httpResult = httpResponse.getResponseCode();

			Logger.println(SpringProjectAPITests.class, "TestK005checkProjectSettingsInvalidProjectID()", "HttpResult is: " + httpResult);
			assertEquals(HttpURLConnection.HTTP_NOT_FOUND, httpResult);

			JsonObject responseBody = httpResponse.getResponseBodyAsJsonObject();
			assertEquals("failed", responseBody.getString("status"));
		}
		catch( Exception e ) {
			Logger.println(SpringProjectAPITests.class, "TestK005checkProjectSettingsInvalidProjectID()", "Exception occurred when setting debug port: " + e.getMessage(),e);
			fail("Exception occurred when setting debug port");
		}
	}

	@Test(timeout=30000) //30 seconds timeout
	public void TestK006checkProjectSettingsNoSettings() {
		try {
			String projectID = MicroclimateTestUtils.getProjectID(projectName, testType);
			String SettingsAPI = MicroclimateTestUtils.getSettingsAPI(projectID);
			String urlParameters = "{\"testParam\": [{\"name\": \"debugPort\",\"value\": \"7888\"}]}";
			HttpResponse httpResponse = MicroclimateTestUtils.callAPIBodyParameters(SettingsAPI, urlParameters, PROTOCOL, PORT, "POST", testType);
			int httpResult = httpResponse.getResponseCode();

			Logger.println(SpringProjectAPITests.class, "TestK006checkProjectSettingsNoSettings()", "HttpResult is: " + httpResult);
			assertEquals(HttpURLConnection.HTTP_BAD_REQUEST, httpResult);

		}
		catch( Exception e ) {
			Logger.println(SpringProjectAPITests.class, "TestK006checkProjectSettingsNoSettings()", "Exception occurred when setting debug port: " + e.getMessage(),e);
			fail("Exception occurred when setting debug port");
		}
	}

	@Test(timeout = 180000) // 3 mins timeout
	public void TestK007changeDebugPortRunMode() {
		//directly return, since icp does not support debugMode.
		if(testType == "icp") {
			return;
		}
		try {
			final String projectSettingsChangedEvent = "projectSettingsChanged";
			String[] eventsOfInterest = {projectSettingsChangedEvent};
			String projectID = MicroclimateTestUtils.getProjectID(projectName, testType);
			String SettingsAPI = MicroclimateTestUtils.getSettingsAPI(projectID);
			String urlParameters = "{\"settings\": {\"internalDebugPort\" : \"7888\"}}";
			MicroclimateTestUtils.PairedResponse pairedResponse = MicroclimateTestUtils.callAPIBodyParametersWSocketResponse(SettingsAPI, urlParameters, PROTOCOL, PORT, "POST", testType, eventsOfInterest, 150);
			int httpResult = pairedResponse.httpResponse.getResponseCode();
			Logger.println(SpringProjectAPITests.class, "TestK008changeDebugPortRunMode()", "HttpResult is: " + httpResult);
			assertEquals(HttpURLConnection.HTTP_ACCEPTED, httpResult);

			JsonObject responseBody = pairedResponse.httpResponse.getResponseBodyAsJsonObject();
			assertNotNull(responseBody.getString("operationId"));

			assertEquals("Expecting only 1 socket event", 1, pairedResponse.socketEvents.length);
			SocketEvent se = pairedResponse.socketEvents[0];
			Logger.log("Socket msg: " + se.getMsg());
			assertEquals("Unexpected socket event received", projectSettingsChangedEvent, se.getMsg());
			Logger.log("Socket event details: " + se.getDetails().toString());

			JSONObject socketResponseBody = se.getDetails();
			assertNotNull(socketResponseBody);
			assertNotNull(socketResponseBody.getString("operationId"));
			assertNotNull(socketResponseBody.getString("projectID"));
			assertEquals(projectID, socketResponseBody.getString("projectID"));
			assertNotNull(socketResponseBody.getString("name"));
			assertEquals("internalDebugPort", socketResponseBody.getString("name"));
			assertEquals("success", socketResponseBody.getString("status"));
			// should get stuck in Starting state now

		}
		catch( Exception e ) {
			Logger.println(SpringProjectAPITests.class, "TestK008changeDebugPortRunMode()", "Exception occurred when setting debug port: " + e.getMessage(),e);
			fail("Exception occurred when setting debug port");
		}
	}

	@Test(timeout=600000) //10 mins timeout
	public void TestK008changeDebugModeandCheckDebugPort() {
		//directly return, since icp does not support debugMode.
		if(testType == "icp") {
			return;
		}
		try {
			final String projectRestartResultEvent = "projectRestartResult";
			String[] eventsOfInterest = {projectRestartResultEvent};
			String projectID = MicroclimateTestUtils.getProjectID(projectName, testType);
			String urlParameters = "{\"action\": \"restart\", \"projectID\": \"" + projectID + "\",\"startMode\": \"debug\"}";
			MicroclimateTestUtils.PairedResponse pairedResponse = MicroclimateTestUtils.callAPIBodyParametersWSocketResponse(ACTION_API, urlParameters, PROTOCOL, PORT, "POST", testType, eventsOfInterest, 540);
			int httpResult = pairedResponse.httpResponse.getResponseCode();
			Logger.println(SpringProjectAPITests.class, "TestK009changeDebugModeandCheckDebugPort()", "HttpResult is: " + httpResult);
			assertEquals(HttpURLConnection.HTTP_ACCEPTED, httpResult);

			JsonObject responseBody = pairedResponse.httpResponse.getResponseBodyAsJsonObject();
			assertNotNull(responseBody.getString("operationId"));

			assertEquals("Expecting only 1 socket event", 1, pairedResponse.socketEvents.length);
			SocketEvent se = pairedResponse.socketEvents[0];
			Logger.log("Socket msg: " + se.getMsg());
			assertEquals("Unexpected socket event received", projectRestartResultEvent, se.getMsg());
			Logger.log("Socket event details: " + se.getDetails().toString());

			JSONObject socketResponseBody = se.getDetails();
			assertNotNull(socketResponseBody);
			assertNotNull(socketResponseBody.getString("operationId"));
			assertNotNull(socketResponseBody.getString("projectID"));
			assertEquals(projectID, socketResponseBody.getString("projectID"));
			assertNotNull(socketResponseBody.getJSONObject("ports"));
			assertNotNull(socketResponseBody.getJSONObject("ports").getString("exposedPort"));
			assertNotNull(socketResponseBody.getJSONObject("ports").getString("internalPort"));
			assertNotNull(socketResponseBody.getJSONObject("ports").getString("exposedDebugPort"));
			assertNotNull(socketResponseBody.getJSONObject("ports").getString("internalDebugPort"));
			assertEquals("success", socketResponseBody.getString("status"));
			assertEquals("7888", socketResponseBody.getJSONObject("ports").getString("internalDebugPort"));
			// should get stuck in Starting state now

		}
		catch( Exception e ) {
			Logger.println(SpringProjectAPITests.class, "TestK009changeDebugModeandCheckDebugPort()", "Exception occurred when setting debug port: " + e.getMessage(),e);
			fail("Exception occurred when setting debug port");
		}
	}

	@Test(timeout = 180000) // 3 mins timeout
	public void TestK009changeApplicationPortNotExposedPort() {
		try {
			final String projectSettingsChangedEvent = "projectSettingsChanged";
			String[] eventsOfInterest = { projectSettingsChangedEvent };
			String projectID = MicroclimateTestUtils.getProjectID(projectName, testType);
			String SettingsAPI = MicroclimateTestUtils.getSettingsAPI(projectID);
			String urlParameters = "{\"settings\": {\"internalPort\" : \"4321\"}}";

			MicroclimateTestUtils.PairedResponse pairedResponse = MicroclimateTestUtils
					.callAPIBodyParametersWSocketResponse(SettingsAPI, urlParameters, PROTOCOL, PORT, "POST", testType,
							eventsOfInterest, 300);
			int httpResult = pairedResponse.httpResponse.getResponseCode();
			Logger.println(SpringProjectAPITests.class, "TestK010changeApplicationPortNotExposedPort()",
					"HttpResult is: " + httpResult);
			assertEquals(HttpURLConnection.HTTP_ACCEPTED, httpResult);

			JsonObject responseBody = pairedResponse.httpResponse.getResponseBodyAsJsonObject();
			Logger.println(SpringProjectAPITests.class, "TestK010changeApplicationPortNotExposedPort()",
					"OperationId is: " + responseBody.getString("operationId"));
			assertNotNull(responseBody.getString("operationId"));

			assertEquals("Expecting only 1 socket event", 1, pairedResponse.socketEvents.length);
			SocketEvent se = pairedResponse.socketEvents[0];
			Logger.log("Socket msg: " + se.getMsg());
			assertEquals("Unexpected socket event received", projectSettingsChangedEvent, se.getMsg());
			Logger.log("Socket event details: " + se.getDetails().toString());

			JSONObject socketResponseBody = se.getDetails();
			assertNotNull(socketResponseBody);
			assertNotNull(socketResponseBody.getString("operationId"));
			assertNotNull(socketResponseBody.getString("projectID"));
			assertEquals(projectID, socketResponseBody.getString("projectID"));
			assertEquals("failed", socketResponseBody.getString("status"));

		} catch (Exception e) {
			Logger.println(SpringProjectAPITests.class, "TestK010changeApplicationPortNotExposedPort()",
					"Exception occurred when setting application port: " + e.getMessage(), e);
			fail("Exception occurred when setting application port");
		}
	}

	// Perform the change Application Port in the end before delete, because the app will not be in started state
	@Test(timeout = 1200000) // 20 mins timeout
	public void TestK010changeApplicationPortExposedPort() {
		assertNotNull("exposedPort for project " + projectName +" is null", exposedPort);
		String Dockerfile = "Dockerfile";
		String content = "EXPOSE 4321";

		try {
			final String projectSettingsChangedEvent = "projectSettingsChanged";
			String[] eventsOfInterest = { projectSettingsChangedEvent };
			String projectID = MicroclimateTestUtils.getProjectID(projectName, testType);
			String SettingsAPI = MicroclimateTestUtils.getSettingsAPI(projectID);
			String urlParameters = "{\"settings\": {\"internalPort\" : \"4321\"}}";

			// Expose the port and confirm the exposed port
			MicroclimateTestUtils.updateDockerFile(testType, projectName, Dockerfile, content);

			while(true) {
				if(MicroclimateTestUtils.checkContainerPortExposed(projectName, testType)) {
					break;
				} else {
					Thread.sleep(3000);
				}
			}

			// change the port
			MicroclimateTestUtils.PairedResponse pairedResponse = MicroclimateTestUtils
					.callAPIBodyParametersWSocketResponse(SettingsAPI, urlParameters, PROTOCOL, PORT, "POST", testType,
							eventsOfInterest, 300);
			int httpResult = pairedResponse.httpResponse.getResponseCode();
			Logger.println(SpringProjectAPITests.class, "TestK011changeApplicationPortExposedPort()",
					"HttpResult is: " + httpResult);
			assertEquals(HttpURLConnection.HTTP_ACCEPTED, httpResult);

			JsonObject responseBody = pairedResponse.httpResponse.getResponseBodyAsJsonObject();
			Logger.println(SpringProjectAPITests.class, "TestK011changeApplicationPortExposedPort()",
					"OperationId is: " + responseBody.getString("operationId"));
			assertNotNull(responseBody.getString("operationId"));

			assertEquals("Expecting only 1 socket event", 1, pairedResponse.socketEvents.length);
			SocketEvent se = pairedResponse.socketEvents[0];
			Logger.log("Socket msg: " + se.getMsg());
			assertEquals("Unexpected socket event received", projectSettingsChangedEvent, se.getMsg());
			Logger.log("Socket event details: " + se.getDetails().toString());

			JSONObject socketResponseBody = se.getDetails();
			assertNotNull(socketResponseBody);
			assertNotNull(socketResponseBody.getString("operationId"));
			assertNotNull(socketResponseBody.getString("projectID"));
			assertEquals(projectID, socketResponseBody.getString("projectID"));
			assertEquals("success", socketResponseBody.getString("status"));
			assertEquals("4321", socketResponseBody.getJSONObject("ports").getString("internalPort"));
			Logger.log("New Exposed Port: " + socketResponseBody.getJSONObject("ports").getString("internalPort"));
			// Logger.log("New Exposed Port: " + socketResponseBody.getString("ports"));
			// assertEquals(true, socketResponseBody.getString("ports").contains("\"internalPort\":\"4321\""));

		} catch (Exception e) {
			Logger.println(SpringProjectAPITests.class, "TestK011changeApplicationPortExposedPort()",
					"Exception occurred when setting application port: " + e.getMessage(), e);
			fail("Exception occurred when setting application port");
		}

	}


	// ==================================================
	// NOTE: perform any tests that modify/delete project artifacts
	// right before project delete as that could impact other tests
	// ==================================================

	@Test(timeout=30000) //30 seconds timeout
	public void TestY001actionValidate() {
		final String projectValidatedEvent = "projectValidated";
		try {
			String projectID = MicroclimateTestUtils.getProjectID(projectName, testType);
			String urlParameters = "{\"action\": \"validate\",\"projectID\": \"" + projectID + "\",\"projectType\": \"spring\",\"location\": \"/codewind-workspace/" + projectName + "\"}";
			Logger.println(SpringProjectAPITests.class, "TestY001actionValidate()", "Validation test with URL parameters: " + urlParameters);
			String[] eventsOfInterest = {projectValidatedEvent};
			MicroclimateTestUtils.PairedResponse pairedResponse = MicroclimateTestUtils.callAPIBodyParametersWSocketResponse(ACTION_API, urlParameters, PROTOCOL, PORT, "POST", testType, eventsOfInterest, 5);
			int httpResult = pairedResponse.httpResponse.getResponseCode();
			assertEquals("Expecting only 1 socket event", 1, pairedResponse.socketEvents.length);
			SocketEvent se = pairedResponse.socketEvents[0];
			Logger.println(SpringProjectAPITests.class, "TestY001actionValidate()", "Socket msg     : " + se.getMsg());
			assertEquals("Unexpected socket event received", projectValidatedEvent, se.getMsg());
			Logger.println(SpringProjectAPITests.class, "TestY001actionValidate()", "Socket event details : " + se.getDetails().toString());
			JSONArray results = se.getDetails().getJSONArray("results");

			assertNotNull(results);

			// Expecting validation to fail
			assertEquals("Expected 0 results", 0, results.length());
			assertEquals("success", se.getDetails().getString("status"));

			Logger.println(SpringProjectAPITests.class, "TestY001actionValidate()", "HttpResult is: " + httpResult);
			assertEquals(HttpURLConnection.HTTP_ACCEPTED, httpResult);
		}
		catch( Exception e ) {
			Logger.println(SpringProjectAPITests.class, "TestY001actionValidate()", "Exception occurred during project action: " + e.getMessage(),e);
			fail("Exception occurred during project action");
		}

		return;
	}

	@Test(timeout=30000) //30 seconds timeout
	public void TestY002actionValidateMissingPOM() throws Exception {
		MicroclimateTestUtils.deleteFile(MicroclimateTestUtils.workspace + projectName + "/pom.xml");
		final String projectValidatedEvent = "projectValidated";
		try {
			String projectID = MicroclimateTestUtils.getProjectID(projectName, testType);
			String urlParameters = "{\"action\": \"validate\",\"projectID\": \"" + projectID + "\",\"projectType\": \"spring\",\"location\": \"/codewind-workspace/" + projectName + "\"}";
			Logger.println(SpringProjectAPITests.class, "TestY002actionValidateMissingPOM()", "Validation test with URL parameters: " + urlParameters);
			String[] eventsOfInterest = {projectValidatedEvent};
			MicroclimateTestUtils.PairedResponse pairedResponse = MicroclimateTestUtils.callAPIBodyParametersWSocketResponse(ACTION_API, urlParameters, PROTOCOL, PORT, "POST", testType, eventsOfInterest, 5);
			int httpResult = pairedResponse.httpResponse.getResponseCode();
			assertEquals("Expecting only 1 socket event", 1, pairedResponse.socketEvents.length);
			SocketEvent se = pairedResponse.socketEvents[0];
			Logger.println(SpringProjectAPITests.class, "TestY002actionValidateMissingPOM()", "Socket msg     : " + se.getMsg());
			assertEquals("Unexpected socket event received", projectValidatedEvent, se.getMsg());
			Logger.println(SpringProjectAPITests.class, "TestY002actionValidateMissingPOM()", "Socket event details : " + se.getDetails().toString());
			JSONArray results = se.getDetails().getJSONArray("results");

			assertNotNull(results);

			// Expecting validation to fail
			assertEquals("Expected 1 result", 1, results.length());
			JSONObject resultsObj = results.getJSONObject(0);
			assertEquals("error", resultsObj.getString("severity"));
			assertEquals("pom.xml", resultsObj.getString("filename"));
			assertEquals(projectName + "/pom.xml", resultsObj.getString("filepath"));
			assertEquals("missing", resultsObj.getString("type"));
			assertEquals("Missing required file", resultsObj.getString("label"));
			assertEquals("pom.xml is required but was not found.", resultsObj.getString("details"));

			Logger.println(SpringProjectAPITests.class, "TestY002actionValidate()", "HttpResult is: " + httpResult);
			assertEquals(HttpURLConnection.HTTP_ACCEPTED, httpResult);
		}
		catch( Exception e ) {
			Logger.println(SpringProjectAPITests.class, "TestY002actionValidateMissingPOM()", "Exception occurred during project action: " + e.getMessage(),e);
			fail("Exception occurred during project action");
		}

		return;

	}

	@Test(timeout=300000) //5 mins timeout
	public void TestZ002projectDelete() {
		final String projectDeletionEvent = "projectDeletion";

		try {
			String projectID = MicroclimateTestUtils.getProjectID(projectName, testType);
			String api = PROJECTS_API + projectID;
			String url = MicroclimateTestUtils.getBaseURL(testType, PORT, PROTOCOL) + api;

			String[] eventsOfInterest = {projectDeletionEvent};
			MicroclimateTestUtils.PairedResponse pairedResponse = MicroclimateTestUtils.callAPIURLParametersWSocketResponse(url, "DELETE", testType, eventsOfInterest, 300);
			int httpResult = pairedResponse.httpResponse.getResponseCode();

			Logger.println(SpringProjectAPITests.class, "TestZ002projectDelete()", "HttpResult is: " + httpResult);
			assertEquals(HttpURLConnection.HTTP_ACCEPTED, httpResult);

			assertEquals("Expecting only 1 socket event", 1, pairedResponse.socketEvents.length);
			SocketEvent se = pairedResponse.socketEvents[0];
			Logger.println(SpringProjectAPITests.class, "TestZ002projectDelete()", "Socket msg: " + se.getMsg());
			assertEquals("Unexpected socket event received", projectDeletionEvent, se.getMsg());
			Logger.println(SpringProjectAPITests.class, "TestZ002projectDelete()", "Socket event details: " + se.getDetails().toString());

			JSONObject socketResponseBody = se.getDetails();
			assertNotNull(socketResponseBody);
			assertNotNull(socketResponseBody.getString("operationId"));
			assertEquals(projectID, socketResponseBody.getString("projectID"));
			assertEquals("success", socketResponseBody.getString("status"));
		}
		catch( Exception e ) {
			Logger.println(SpringProjectAPITests.class, "TestZ002projectDelete()", "Exception occurred during project delete: " + e.getMessage(),e);
			fail("Exception occurred during project delete");
		}
	}
}
