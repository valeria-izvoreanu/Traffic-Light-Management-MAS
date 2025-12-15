plugins {
    id("application")
    id("java")
    id("org.openjfx.javafxplugin") version "0.0.13"
}

group = "org.traffic"
version = "1.0-SNAPSHOT"

repositories {
    mavenCentral()
}

javafx {
    version = "17.0.6"
    modules("javafx.controls", "javafx.fxml", "javafx.graphics")
}

dependencies {
    testImplementation(platform("org.junit:junit-bom:5.10.0"))
    testImplementation("org.junit.jupiter:junit-jupiter")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    implementation(files("libs/jade-4.6.0.jar"))
    implementation(files("libs/commons-codec-1.15.jar"))
}

tasks.test {
    useJUnitPlatform()
}
application {
    mainClass.set("visuals.TrafficView")
}

tasks.register("runSim") {
    group = "application"
    description = "Runs the JavaFX Visualization + JADE"
    dependsOn("run")
}

tasks.register<JavaExec>("runMasConsole") {
    group = "jade"
    description = "Launches JADE in console mode (No GUI Map)"

    classpath = sourceSets["main"].runtimeClasspath
    mainClass.set("jade.Boot")

    args(
        "-gui",
        "-agents",
        // ROW 0
        "Node_0_0:agents.IntersectionAgent(150,150,Node_0_1,Node_1_0);" +
        "Node_0_1:agents.IntersectionAgent(350,150,Node_0_0,Node_0_2,Node_1_1);" +
        "Node_0_2:agents.IntersectionAgent(550,150,Node_0_1,Node_1_2);" +
        // ROW 1
        "Node_1_0:agents.IntersectionAgent(150,350,Node_0_0,Node_2_0,Node_1_1);" +
        "Node_1_1:agents.IntersectionAgent(350,350,Node_0_1,Node_2_1,Node_1_0,Node_1_2);" + // Center
        "Node_1_2:agents.IntersectionAgent(550,350,Node_0_2,Node_2_2,Node_1_1);" +
        // ROW 2
        "Node_2_0:agents.IntersectionAgent(150,550,Node_1_0,Node_2_1);" +
        "Node_2_1:agents.IntersectionAgent(350,550,Node_2_0,Node_2_2,Node_1_1);" +
        "Node_2_2:agents.IntersectionAgent(550,550,Node_2_1,Node_1_2)"
    )
}