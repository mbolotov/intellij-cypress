## IntelliJ Cypress integration plugin
Integrates <b>Cypress</b> under the common Intellij test framework.
### Compatibility
As the plugin depends on *JavaLanguage* and *NodeJS* plugins, so it requires a commercial version of IDEA (Ultimate, WebStorm etc) 
### Build
```bash
./gradlew buildPlugin
````
### Run
Either start IDE bundled with plugin via gradle:
```bash
./gradlew runIdea
```                                             
Or install built plugin manually in the Settings->Plugin section of IDEA
### Usage
#### Cypress project setup
Plugin requires a service reporter to be run on the Cypress side. So a Cypress project has to declare a *cypress-intellij-reporter* package dependency:
```bash
npm i cypress-intellij-reporter -D
````                              
#### Test run configurations
Plugin introduces a dedicated Cypress [run configuration](https://www.jetbrains.com/help/idea/run-debug-configuration.html) type
You can create a run config from either file view (directory, spec file) or directly from the code:
![](../media/media/createFromDir.png?raw=true) | ![](../media/media/createFromSrc.png?raw=true)

Notice that *cypress-intellij-reporter* introduces *mocha* dependency that enables the mocha test framework in IDEA automatically. So please do not confuse Cypress and Mocha run types: ![](../media/media/confuseMocha.png?raw=true)

Cypress tests are hardly to be run directly by mocha runner (so the mocha support for Cypress tests to be better off by Jetbrains).  

#### Running tests
Simply start your configuration ~~and take a deep breth~~. You can watch test status live on the corresponding tab:   
![](../media/media/run.png?raw=true)

You can navigate from a test entry in the test tab to the source code of this test just by clicking on it.

#### Limitations and Workarounds
1. No rerun failed tests only feature because Cypress is unable to run tests [defined by a grep pattern](https://github.com/cypress-io/cypress/issues/1865)
2. Run a single test feature is implemented by creating an additional spec on the fly which has the test marked with **.only** modifier. So it may work incorrectly when a test spec already contains '.only' tests    
  
### Contribute

Contributions are always welcome!



