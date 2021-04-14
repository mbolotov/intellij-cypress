# IntelliJ Cypress integration plugin
Integrates <b>Cypress.io</b> under the common Intellij test framework.
## Compatibility
As the plugin depends on *JavaLanguage* and *NodeJS* plugins, so it requires a commercial version of IDEA (Ultimate, WebStorm etc) 
## Install
Plugin can be installed from the Jetbrains Marketplace. Open '*Settings/Preferences* -> *Plugins*' menu item and type '**Cypress**' in the search bar. See [here](https://www.jetbrains.com/help/idea/managing-plugins.html) for details. Please note that base and Pro versions are not expected to run together so you should disable or uninstall one of them to use the other. 
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

Pro version also supports running Cucumber-Cypress tests.

#### Runner limitations:
1. No rerun failed tests only feature because Cypress is unable to run tests [defined by a grep pattern](https://github.com/cypress-io/cypress/issues/1865)
2. Run a single test feature is implemented by modifying the sources on the fly and mark the test with **.only** modifier automatically. So it may work incorrectly when a test spec already contains '.only' tests    


### Debugging tests ([Pro version](https://plugins.jetbrains.com/plugin/13987-cypress-pro) only)
<p><b>Cypress Support Pro</b> plugin is able to debug Cypress test execution.</p>
<p>Video overview: https://www.youtube.com/watch?v=FIo62E1OMO0</p> 
<p>It supports all the common IDE debug features: step-by-step execution, run to cursor, variable examining, expression evaluation, breakpoints (including conditional), etc.<br>
It works for both headed and headless modes as well as in the interactive mode</p>

![](../media/debugger.png?raw=true)

#### Debugger limitations:

1. Firefox is not currently supported.
2. In some rare cases IDE can't map sources correctly so breakpoints will not hit in this case. Use <b>debugger</b> statement to suspend the execution
3. IDE need some time (usually less than a second) to attach breakpoints to Chrome. So your breakpotins could not be hit when test case executed fast.
4. Ansynchronous Cypress commands cannot be debugged as they would be synchrounous. See [here](https://docs.cypress.io/guides/guides/debugging.html#Debug-just-like-you-always-do) for details and workarounds.

### Built-In test recorder ([Pro version](https://plugins.jetbrains.com/plugin/13987-cypress-pro) only)

<b>Cypress Support Pro</b> v. 1.5+ can record UI actions and insert generated code directly into the testcase.
At the Cypress Runner side, the recorder UI looks like this:

![](../media/recorderUi.png?raw=true)

A video overview: https://youtu.be/FgnHYwmguFI

It has pluggable architecture, so the user can upload her own recorder plugin for the event capturing and code generation. Refer to [this wiki page](../../wiki/Recorder-API) for more information about Recorder API

By default, it uses the plugin available here: [script/recorder.js](https://github.com/mbolotov/intellij-cypress/blob/master/script/recorder.js). Feel free to copy it localy and modify for your needs. 

### Fast test restart ([Pro version](https://plugins.jetbrains.com/plugin/13987-cypress-pro) only)
Starting version <b>1.2.1</b>, plugin is able to reuse a running Cypress instance to restart the test fast.<br>
First, you need to start test either with <b>--no-exit</b> option or in the <b>interactive</b> mode.
Subsequent test runs will reuse the running browser instance.<br>
Uncheck <b>'Allow parallel run'</b> box in the run configuration to disable this feature.  
#### Fast test restart limitations:  
Cypress does not reflect code changes when run in non-interactive mode by design, see [here](https://github.com/cypress-io/cypress/issues/3665#issuecomment-470683348)

### Autocomplete and navigation to the source ([Pro version](https://plugins.jetbrains.com/plugin/13987-cypress-pro) only)
Plugin adds autocomplete contribution for the following elements:
1. Alias references inside `cy.get()` and `cy.wait()`
2. Fixture references inside `cy.fixture()` and `cy.route(..., 'fx:')`
3. Custom cy commands (in JS code only, use type definitions for TypeScript)

Also, all of those elements support navigation to the source by clicking on a reference 

See it in action: https://youtu.be/WR5ywX01YbQ  

![](../media/aliasCompletion.png?raw=true)
#### Autocomplete background and limitations
1. Suggested alias references are not guaranteed to be available in runtime due to the dynamic nature of JS language.  
2. Plugin searches available fixtures using settings from the cypress.json configuration. Default values are used if no configuration file found.
3. Plugin makes a list of available custom cy commands by looking for `Cypress.Commands.add` function references within the project  

### Opening test screenshot from the test tree view
Plugin has a shortcut action to open test screenshot from the test tree view:
![](../media/showScreenshot.png?raw=true)

If a test holds screenshots in the folder, action will either suggest selecting from the list or pick up the latest screenshot. 

This behavior can be configured in the settings:
![](../media/screenshotConfig.png?raw=true)

### Running Cucumber-Cypress tests ([Pro version](https://plugins.jetbrains.com/plugin/13987-cypress-pro) only) 
Starting from version 1.6, the plugin can start a cucumber test.

The exectuion depends on the [cypress-cucumber-preprocessor](https://github.com/TheBrainFamily/cypress-cucumber-preprocessor) so you need to add the following dependency to your project:

`npm install --save-dev cypress-cucumber-preprocessor`

To start a single scenario, the plugin will automatically add (and remove at the end) a `@focus` tag.

![](../media/cucumberRun.png?raw=true)


# Cypress Support vs Cypress Support Pro comparison

Feature | Cypress Support | Cypress Support Pro 
-----|----|------
Run tests from IDE|:heavy_check_mark:|:heavy_check_mark:
Debug tests from IDE|:o:|:heavy_check_mark:
Record tests from IDE|:o:|:heavy_check_mark:
Create Test configuration from the code|:heavy_check_mark:|:heavy_check_mark:
Test execution live view|:heavy_check_mark:|:heavy_check_mark: 
Open test screenshot from the tree |:heavy_check_mark:|:heavy_check_mark:
Custom commands as first class citizen|:o:|:heavy_check_mark:
Selector language autocomplete and syntax highlighting|:o:|:heavy_check_mark:  
Extended autocomplete and navigation for aliases and fixtures|:o:|:heavy_check_mark:
Fast test restart using the same Chrome instance|:o:|:heavy_check_mark:
Run Cucumber tests from IDE|:o:|:heavy_check_mark:
Run Cucumber tests from IDE|:o:|:heavy_check_mark:

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
