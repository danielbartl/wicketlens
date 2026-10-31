package dev.wicketlens

import com.intellij.openapi.project.Project
import com.intellij.testFramework.DumbModeTestUtils
import com.intellij.testFramework.PlatformTestUtil
import com.intellij.testFramework.fixtures.IdeaTestFixtureFactory
import com.intellij.testFramework.fixtures.JavaCodeInsightFixtureTestCase
import dev.wicketlens.mcp.*
import kotlinx.coroutines.*

class WicketMcpProjectLifecycleTest : JavaCodeInsightFixtureTestCase() {
    private fun <T> await(result: Deferred<T>): T {
        PlatformTestUtil.waitWithEventsDispatching("MCP project disposal", { result.isCompleted }, 10)
        return runBlocking { result.await() }
    }
    private fun requests(scope: CoroutineScope, target: Project) = WicketMcpReport.entries.map { view -> scope.async {
        WicketPageAnalysisRequest().execute(target, "example.ExamplePage", view = view,
            componentPath = if (view == WicketMcpReport.CONTEXT) "title" else null)
    } }
    private fun assertUnavailable(result: Deferred<String>) {
        try { await(result); fail("Expected project unavailable") }
        catch (failure: PageAnalysisFailure) { assertEquals("PROJECT_UNAVAILABLE", failure.code) }
    }
    fun testAlreadyDisposedProjectFailsPromptlyForAllViews() {
        val fixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder("closed-mcp-project").fixture
        fixture.setUp()
        val target = fixture.project
        fixture.tearDown()
        assertTrue(target.isDisposed)
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try { requests(scope, target).forEach(::assertUnavailable) } finally { scope.cancel() }
    }
    fun testDisposalReleasesAllIndexingWaitsWithoutWaitingForTheDeadline() {
        val fixture = IdeaTestFixtureFactory.getFixtureFactory().createFixtureBuilder("closing-mcp-project").fixture
        fixture.setUp()
        var tornDown = false
        val scope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
        try {
            val target = fixture.project
            DumbModeTestUtils.startEternalDumbModeTask(target)
            val results = requests(scope, target)
            val interval = scope.async { delay(150) }
            await(interval)
            assertTrue(results.all { !it.isCompleted })
            fixture.tearDown(); tornDown = true
            results.forEach(::assertUnavailable)
            assertTrue(target.isDisposed)
            assertTrue(scope.isActive)
        } finally {
            scope.cancel()
            if (!tornDown) fixture.tearDown()
        }
    }
}
