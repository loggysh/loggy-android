#loggy-android

Android Client for Loggy

###Install Loggy library in build.gradle [Search Maven For Latest Version](https://search.maven.org/artifact/sh.loggy/loggy)
```groovy
    implementation 'sh.loggy:loggy:<version>'
```

###Setup in MainApplication onCreate
```kotlin
    override fun onCreate() {
        Loggy.setup(
                this@MainApplication,
                "<user-id>",
                "<device-name>" // Lady Ada Lovelace
            )
    }
```

###Send Logs
```kotlin
    Loggy.log(priority, tag, message, t)
```

###Send logs using Timber Tree
```kotlin
    class LoggyTree() : Timber.Tree() {
        override fun log(priority: Int, tag: String?, message: String, t: Throwable?) {
            Loggy.log(priority, tag, message, t)
        }
    }
```

###Plant TimberTree
Add this to application onCreate
```kotlin
     Timber.plant(LoggyTree())
```