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
Brief video overview: https://www.youtube.com/watch?v=1gjjy0RQeBw 
#### Cypress project setup
Plugin requires a service reporter to be run on the Cypress side. So a Cypress project has to declare a *cypress-intellij-reporter* package dependency:
```bash
npm i cypress-intellij-reporter -D
````                              
#### Test run configurations
Plugin introduces a dedicated Cypress [run configuration](https://www.jetbrains.com/help/idea/run-debug-configuration.html) type
You can create a run config from either file view (directory, spec file) or directly from the code

file view | code view 
------------ | -------------
![](../media/createFromDir.png?raw=true) | ![](../media/createFromSrc.png?raw=true)

Notice that *cypress-intellij-reporter* introduces *mocha* dependency that enables the mocha test framework in IDEA automatically. So please do not confuse Cypress and Mocha run types: ![](../media/confuseMocha.png?raw=true)

Cypress tests are hardly to be run directly by mocha runner (so the mocha support for Cypress tests to be better off by Jetbrains).  

#### Running tests
Simply start your configuration ~~and take a deep breth~~. You can watch test status live on the corresponding tab:   
![](../media/run.png?raw=true)

You can navigate from a test entry in the test tab to the source code of this test just by clicking on it.<br>

##### Runner limitations:
1. No rerun failed tests only feature because Cypress is unable to run tests [defined by a grep pattern](https://github.com/cypress-io/cypress/issues/1865)
2. Run a single test feature is implemented by modifying the sources on the fly and mark the test with **.only** modifier automatically. So it may work incorrectly when a test spec already contains '.only' tests    


#### Debugging tests (Pro version only)
<p><b>CypressPro</b> plugin is able to debug Cypress test execution.</p> 
<p>It supports all the common IDE debug features: step-by-step execution, run to cursor, variable examining, expression evaluation, breakpoints (including conditional), etc.<br>
It works for both headed and headless modes as well as in the interactive mode</p>

![](../media/debugger.png?raw=true)

##### Debugger limitations:

1. Chrome is only supported. Plugin will automatically add '-b chrome' option to the command line.
2. In some rare cases IDE can't map sources correctly so breakpoints will not hit in this case. Use <b>debugger</b> statement to suspend the execution

#### Fast test restart (Pro version only)
Starting version <b>1.2.1</b>, plugin is able to reuse a running Cypress instance to restart the test fast.<br>
First, you need to start test either with <b>--no-exit</b> option or in the <b>interactive</b> mode.
Subsequent test runs will reuse the running browser instance.<br>
Uncheck <b>'Allow parallel run'</b> box in the run configuration to disable this feature.  
##### Fast test restart limitations:  
Cypress does not reflect code changes then run in non-interactive mode by design, see [here](https://github.com/cypress-io/cypress/issues/3665#issuecomment-470683348)

