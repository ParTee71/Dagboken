// Init-skript för fjärrsessioner: Maven Central nås via Googles spegel, eftersom
// repo.maven.apache.org ofta svarar 429 (Too Many Requests) mot sessionens Gradle.
// session-start.sh kopierar filen till ~/.gradle/init.d/; CI och Android Studio påverkas inte.
val mirror = "https://maven-central.storage-download.googleapis.com/maven2/"
fun RepositoryHandler.useMirror() {
    all {
        if (this is MavenArtifactRepository && url.toString().contains("repo.maven.apache.org")) {
            setUrl(mirror)
        }
    }
}
beforeSettings {
    pluginManagement.repositories.useMirror()
    dependencyResolutionManagement.repositories.useMirror()
}
allprojects {
    buildscript.repositories.useMirror()
    repositories.useMirror()
}
