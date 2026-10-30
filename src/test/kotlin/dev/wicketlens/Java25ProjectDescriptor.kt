package dev.wicketlens

import com.intellij.openapi.projectRoots.JavaSdk
import com.intellij.openapi.projectRoots.Sdk
import com.intellij.testFramework.LightProjectDescriptor

/** Standalone fixture artifacts do not include the IDE source tree's mock JDK. */
object Java25ProjectDescriptor : LightProjectDescriptor() {
    override fun getSdk(): Sdk = JavaSdk.getInstance().createJdk(
        "WicketLens test JDK",
        System.getProperty("java.home"),
        false,
    )
}
