# IntelliJ Cypress integration plugin
Integrates <b>Cypress.io</b> under the common Intellij test framework.
## Compatibility
As the plugin depends on *JavaLanguage* and *NodeJS* plugins, so it requires a commercial version of IDEA (Ultimate, WebStorm etc) 
## Install
Plugin can be installed from the Jetbrains Marketplace. Open '*Settings/Preferences* -> *Plugins*' menu item and type '**Cypress**' in the search bar. See [here](https://www.jetbrains.com/help/idea/managing-plugins.html) for details. 
## Usage
Brief video overview: https://www.youtube.com/watch?v=1gjjy0RQeBw 
### Test run configurations
Plugin introduces a dedicated Cypress [run configuration](https://www.jetbrains.com/help/idea/run-debug-configuration.html) type
You can create a run config from either file view (directory, spec file) or directly from the code

file view | code view 
------------ | -------------
![](../media/createFromDir.png?raw=true) | ![](../media/createFromSrc.png?raw=true)

Notice that *cypress-intellij-reporter* introduces *mocha* dependency that enables the mocha test framework in IDEA automatically. So please do not confuse Cypress and Mocha run types: ![](../media/confuseMocha.png?raw=true)

### Running tests
Simply start your configuration ~~and take a deep breath~~. You can watch test status live on the corresponding tab:   
![](../media/run.png?raw=true)

You can navigate from a test entry in the test tab to the source code of this test just by clicking on it.<br>


#### Runner limitations:
1. No rerun failed tests only feature because Cypress is unable to run tests [defined by a grep pattern](https://github.com/cypress-io/cypress/issues/1865)
2. Run a single test feature is implemented by modifying the sources on the fly and mark the test with **.only** modifier automatically. So it may work incorrectly when a test spec already contains '.only' tests    


### Debugging tests 
<p>Video overview: https://www.youtube.com/watch?v=FIo62E1OMO0</p> 
<p>It supports all the common IDE debug features: step-by-step execution, run to cursor, variable examining, expression evaluation, breakpoints (including conditional), etc.<br>
It works for both headed and headless modes as well as in the interactive mode</p>

![](../media/debugger.png?raw=true)

#### Debugger limitations:

1. Firefox is not currently supported.
2. In some rare cases IDE can't map sources correctly so breakpoints will not hit in this case. Use <b>debugger</b> statement to suspend the execution
3. IDE need some time (usually less than a second) to attach breakpoints to Chrome. So your breakpotins could not be hit when test case executed fast.
4. Ansynchronous Cypress commands cannot be debugged as they would be synchrounous. See [here](https://docs.cypress.io/guides/guides/debugging.html#Debug-just-like-you-always-do) for details and workarounds.

### Opening test screenshot from the test tree view
Plugin has a shortcut action to open test screenshot from the test tree view:
![](../media/showScreenshot.png?raw=true)

If a test holds screenshots in the folder, action will either suggest selecting from the list or pick up the latest screenshot. 

This behavior can be configured in the settings:
![](../media/screenshotConfig.png?raw=true)

### Running Cucumber-Cypress tests 
Starting from version 1.6, the plugin can start a cucumber test.

The exectuion depends on the [cypress-cucumber-preprocessor](https://github.com/TheBrainFamily/cypress-cucumber-preprocessor) so you need to add the following dependency to your project:

`npm install --save-dev cypress-cucumber-preprocessor`

To start a single scenario, the plugin will automatically add (and remove at the end) a `@focus` tag.

![](../media/cucumberRun.png?raw=true)


## Build plugin from the sources
```bash
./gradlew buildPlugin
````
## Run
Either start IDE bundled with plugin via gradle:
```bash
./gradlew runIdea
```                                             
Or install built plugin manually in the Settings->Plugin section of IDEA
