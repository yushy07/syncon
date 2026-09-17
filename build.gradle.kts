plugins {
    alias(libs.plugins.android.application) apply false
    alias(libs.plugins.kotlin.compose) apply false
    alias(libs.plugins.ksp) apply false
}

buildscript {
    configurations.all {
        resolutionStrategy {
            force("org.jdom:jdom2:2.0.6.1")
            force("org.bouncycastle:bcprov-jdk18on:1.85")
            force("org.bouncycastle:bcpkix-jdk18on:1.85")
            force("org.bitbucket.b_c:jose4j:0.9.6")
            force("org.apache.commons:commons-lang3:3.20.0")
            eachDependency {
                if (requested.group == "io.netty") useVersion("4.2.17.Final")
                if (requested.group == "org.bouncycastle" && requested.name.endsWith("jdk18on")) {
                    useVersion("1.85")
                }
            }
        }
    }
}

allprojects {
    configurations.all {
        resolutionStrategy {
            force("org.jdom:jdom2:2.0.6.1")
            force("org.bouncycastle:bcprov-jdk18on:1.85")
            force("org.bouncycastle:bcpkix-jdk18on:1.85")
            force("org.bitbucket.b_c:jose4j:0.9.6")
            force("org.apache.commons:commons-lang3:3.20.0")
            eachDependency {
                if (requested.group == "io.netty") useVersion("4.2.17.Final")
                if (requested.group == "org.bouncycastle" && requested.name.endsWith("jdk18on")) {
                    useVersion("1.85")
                }
            }
        }
    }
}
