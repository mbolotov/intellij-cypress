const path = require('path')
    , processStdoutWrite = process.stdout.write.bind(process.stdout)
    , processStderrWrite = process.stderr.write.bind(process.stderr)
    , MOCHA = 'mocha';

var doEscapeCharCode = (function () {
    var obj = {};

    function addMapping(fromChar, toChar) {
        if (fromChar.length !== 1 || toChar.length !== 1) {
            throw Error('String length should be 1');
        }
        var fromCharCode = fromChar.charCodeAt(0);
        if (typeof obj[fromCharCode] === 'undefined') {
            obj[fromCharCode] = toChar;
        } else {
            throw Error('Bad mapping');
        }
    }

    addMapping('\n', 'n');
    addMapping('\r', 'r');
    addMapping('\u0085', 'x');
    addMapping('\u2028', 'l');
    addMapping('\u2029', 'p');
    addMapping('|', '|');
    addMapping('\'', '\'');
    addMapping('[', '[');
    addMapping(']', ']');

    return function (charCode) {
        return obj[charCode];
    };
}());

function isAttributeValueEscapingNeeded(str) {
    var len = str.length;
    for (var i = 0; i < len; i++) {
        if (doEscapeCharCode(str.charCodeAt(i))) {
            return true;
        }
    }
    return false;
}

function escapeAttributeValue(str) {
    if (!isAttributeValueEscapingNeeded(str)) {
        return str;
    }
    var res = ''
        , len = str.length;
    for (var i = 0; i < len; i++) {
        var escaped = doEscapeCharCode(str.charCodeAt(i));
        if (escaped) {
            res += '|';
            res += escaped;
        } else {
            res += str.charAt(i);
        }
    }
    return res;
}

/**
 * @param {Array.<string>} list
 * @param {number} fromInclusive
 * @param {number} toExclusive
 * @param {string} delimiterChar one character string
 * @returns {string}
 */
function joinList(list, fromInclusive, toExclusive, delimiterChar) {
    if (list.length === 0) {
        return '';
    }
    if (delimiterChar.length !== 1) {
        throw Error('Delimiter is expected to be a character, but "' + delimiterChar + '" received');
    }
    var addDelimiter = false
        , escapeChar = '\\'
        , escapeCharCode = escapeChar.charCodeAt(0)
        , delimiterCharCode = delimiterChar.charCodeAt(0)
        , result = ''
        , item
        , itemLength
        , ch
        , chCode;
    for (var itemId = fromInclusive; itemId < toExclusive; itemId++) {
        if (addDelimiter) {
            result += delimiterChar;
        }
        addDelimiter = true;
        item = list[itemId];
        itemLength = item.length;
        for (var i = 0; i < itemLength; i++) {
            ch = item.charAt(i);
            chCode = item.charCodeAt(i);
            if (chCode === delimiterCharCode || chCode === escapeCharCode) {
                result += escapeChar;
            }
            result += ch;
        }
    }
    return result;
}

var toString = {}.toString;

/**
 * @param {*} value
 * @return {boolean}
 */
function isString(value) {
    return isStringPrimitive(value) || toString.call(value) === '[object String]';
}

/**
 * @param {*} value
 * @return {boolean}
 */
function isStringPrimitive(value) {
    return typeof value === 'string';
}

function safeFn(fn) {
    return function () {
        try {
            return fn.apply(this, arguments);
        } catch (ex) {
            const message = ex.message || '';
            const stack = ex.stack || '';
            warn(stack.indexOf(message) >= 0 ? stack : message + '\n' + stack);
        }
    };
}

function warn(...args) {
    const util = require('util');
    const str = 'warn mocha-intellij: ' + util.format.apply(util, args) + '\n';
    try {
        processStderrWrite(str);
    } catch (ex) {
        try {
            processStdoutWrite(str);
        } catch (ex) {
            // do nothing
        }
    }
}

function writeToStdout(str) {
    processStdoutWrite(str);
}

function writeToStderr(str) {
    processStderrWrite(str);
}

/**
 * Requires inner mocha module.
 *
 * @param {string} mochaModuleRelativePath  Path to inner mocha module relative to mocha package root directory,
 *                                e.g. <code>"./lib/utils"</code> or <code>"./lib/reporters/base.js"</code>
 * @returns {*} loaded module
 */
function requireMochaModule(mochaModuleRelativePath) {
    const mainFile = require.main.filename;
    const packageDir = findPackageDir(mainFile);
    if (packageDir == null) {
        throw Error('mocha-intellij: cannot require "' + mochaModuleRelativePath +
            '": unable to find package root for "' + mainFile + '"');
    }
    const mochaModulePath = path.join(packageDir, mochaModuleRelativePath);
    if (path.basename(packageDir) === MOCHA) {
        return requireInContext(mochaModulePath);
    }
    try {
        return requireInContext(mochaModulePath);
    } catch (e) {
        const mochaPackageDir = findMochaInnerDependency(packageDir);
        if (mochaPackageDir == null) {
            throw Error('mocha-intellij: cannot require "' + mochaModuleRelativePath +
                '": not found mocha dependency for "' + packageDir + '"');
        }
        return requireInContext(path.join(mochaPackageDir, mochaModuleRelativePath));
    }
}

function requireInContext(modulePathToRequire) {
    const contextRequire = getContextRequire(modulePathToRequire);
    return contextRequire(modulePathToRequire);
}

function getContextRequire(modulePathToRequire) {
    const m = require('module');
    if (typeof m.createRequire === 'function') {
        // https://nodejs.org/api/modules.html#modules_module_createrequire_filename
        // Also, implemented for Yarn Pnp: https://next.yarnpkg.com/advanced/pnpapi/#requiremodule
        return m.createRequire(process.cwd());
    }
    return require;
}

function toUnixPath(path) {
    return path.split("\\").join("/");
}

function findMochaInnerDependency(packageDir) {
    let mochaMainFilePath = require.resolve("mocha", {paths: [packageDir]});
    mochaMainFilePath = toUnixPath(mochaMainFilePath);
    const sepMochaSep = "/mocha/";
    const ind = mochaMainFilePath.lastIndexOf(sepMochaSep);
    if (ind < 0) {
        throw Error("Cannot find mocha package for " + packageDir);
    }
    return mochaMainFilePath.substring(0, ind + sepMochaSep.length - 1);
}

/**
 * Find package's root directory traversing the file system up.
 *
 * @param   {string} startDir Starting directory or file located in the package
 * @returns {?string}         The package's root directory, or null if not found
 */
function findPackageDir(startDir) {
    let dir = startDir;
    while (dir != null) {
        if (path.basename(dir) === 'node_modules') {
            break;
        }
        // try {
        //     const node_modules = path.join(dir, 'node_modules');
        //     if (fs.existsSync(dir)) return dir;
        // } catch (e) {
        //
        // }
        try {
            const packageJson = path.join(dir, 'package.json');
            require.resolve(packageJson, {paths: [process.cwd()]});
            return dir;
        } catch (e) {
        }
        const parent = path.dirname(dir);
        if (dir === parent) {
            break;
        }
        dir = parent;
    }
    return null;
}

/**
 * It's suggested that every Mocha reporter should inherit from Mocha Base reporter.
 * See https://github.com/mochajs/mocha/blob/master/lib/reporters/base.js
 *
 * At least Base reporter is needed to add and update IntellijReporter.stats object that is used by growl reporter.
 * @returns {?function}  The base reporter, or undefined if not found
 */
function requireBaseReporter() {
    const baseReporterPath = './lib/reporters/base.js';
    try {
        const Base = requireMochaModule(baseReporterPath);
        if (typeof Base === 'function') {
            return Base;
        }
        warn('base reporter (' + baseReporterPath + ') is not a function');
    } catch (e) {
        warn('cannot load base reporter from "' + baseReporterPath + '". ', e);
    }
}

function inherit(child, parent) {
  child.prototype = Object.create(parent.prototype, {
    constructor: {
      value: child,
      enumerable: false,
      writable: true,
      configurable: true
    }
  });
}


function Tree(write) {
  /**
   * @type {Function}
   * @protected
   */
  this.writeln = function (str) {
    write(str + '\n');
  };
  /**
   * Invisible root. No messages should be sent to IDE for this node.
   * @type {TestSuiteNode}
   * @public
   */
  this.root = new TestSuiteNode(this, 0, null, 'hidden root', null, null);
  /**
   * @type {number}
   * @protected
   */
  this.nextId = Math.round(Math.random() * 2147483647);
}

Tree.prototype.testingStarted = function () {
  // this.writeln('##teamcity[testingStarted]');
};

Tree.prototype.testingFinished = function () {
  // this.writeln('##teamcity[testingFinished]');
};

/**
 * Node class is a base abstract class for TestSuiteNode and TestNode classes.
 *
 * @param {Tree} tree test tree
 * @param {number} id this node ID. It should be unique among all node IDs that belong to the same tree.
 * @param {TestSuiteNode} parent parent node
 * @param {string} name node name (it could be a suite/spec name)
 * @param {string} type node type (e.g. 'config', 'browser')
 * @param {string} locationPath string that is used by IDE to navigate to the definition of the node
 * @param {string} metaInfo
 * @abstract
 * @constructor
 */
function Node(tree, id, parent, name, type, locationPath, metaInfo) {
  /**
   * @type {Tree}
   * @protected
   */
  this.tree = tree;
  /**
   * @type {number}
   * @protected
   */
  this.id = id;
  /**
   * @type {TestSuiteNode}
   * @public
   */
  this.parent = parent;
  /**
   * @type {string}
   * @public
   */
  this.name = name;
  /**
   * @type {string}
   * @private
   */
  this.type = type;
  /**
   * @type {string}
   * @private
   */
  this.locationPath = locationPath;
  /**
   * @type {string}
   * @private
   */
  this.metaInfo = metaInfo;
  /**
   * @type {NodeState}
   * @protected
   */
  this.state = NodeState.CREATED;
}

/**
 * @param name
 * @constructor
 * @private
 */
function NodeState(name) {
  this.name = name;
}
NodeState.prototype.toString = function() {
    return this.name;
};
NodeState.CREATED = new NodeState('created');
NodeState.REGISTERED = new NodeState('registered');
NodeState.STARTED = new NodeState('started');
NodeState.FINISHED = new NodeState('finished');

/**
 * Changes node's state to 'REGISTERED' and sends corresponding message to IDE.
 * In response to this message IDE will add a node with 'non-spinning' icon to its test tree.
 * @public
 */
Node.prototype.register = function () {
  var text = this.getRegisterMessage();
  this.tree.writeln(text);
  this.state = NodeState.REGISTERED;
};

/**
 * @returns {string}
 * @private
 */
Node.prototype.getRegisterMessage = function () {
  if (this.state === NodeState.CREATED) {
    return this.getInitMessage(false);
  }
  throw Error('Unexpected node state: ' + this.state);
};

/**
 * @param {boolean} running
 * @returns {string}
 * @private
 */
Node.prototype.getInitMessage = function (running) {
  var startCommandName = this.getStartCommandName();
  var text = '##teamcity[';
  text += startCommandName;
  text += ' nodeId=\'' + this.id;
  var parentId = this.parent ? this.parent.id : 0;
  text += '\' parentNodeId=\'' + parentId;
  text += '\' name=\'' + escapeAttributeValue(this.name);
  text += '\' running=\'' + running;
  if (this.type != null) {
    text += '\' nodeType=\'' + this.type;
    if (this.locationPath != null) {
      text += '\' locationHint=\'' + escapeAttributeValue(this.type + '://' + this.locationPath);
    }
  }
  if (this.metaInfo != null) {
    text += '\' metainfo=\'' + escapeAttributeValue(this.metaInfo);
  }
  text += '\']';
  return text;
};

/**
 * Changes node's state to 'STARTED' and sends a corresponding message to IDE.
 * In response to this message IDE will do either of:
 * - if IDE test tree doesn't have a node, the node will be added with 'spinning' icon
 * - if IDE test tree has a node, the node's icon will be changed to 'spinning' one
 * @public
 */
Node.prototype.start = function () {
  if (this.state === NodeState.FINISHED) {
    throw Error("Cannot start finished node");
  }
  if (this.state === NodeState.STARTED) {
    // do nothing in case of starting already started node
    return;
  }
  var text = this.getStartMessage();
  this.tree.writeln(text);
  this.state = NodeState.STARTED;
};

/**
 * @returns {String}
 * @private
 */
Node.prototype.getStartMessage = function () {
  if (this.state === NodeState.CREATED) {
    return this.getInitMessage(true);
  }
  if (this.state === NodeState.REGISTERED) {
    var commandName = this.getStartCommandName();
    return '##teamcity[' + commandName + ' nodeId=\'' + this.id + '\' running=\'true\']';
  }
  throw Error("Unexpected node state: " + this.state);
};

/**
 * @return {string}
 * @abstract
 * @private
 */
Node.prototype.getStartCommandName = function () {
  throw Error('Must be implemented by subclasses');
};

/**
 * Changes node's state to 'FINISHED' and sends corresponding message to IDE.
 * @param {boolean?} finishParentIfLast if true, parent node will be finished if all sibling nodes have already been finished
 * @public
 */
Node.prototype.finish = function (finishParentIfLast) {
  if (this.state !== NodeState.REGISTERED && this.state !== NodeState.STARTED) {
    throw Error('Unexpected node state: ' + this.state);
  }
  var text = this.getFinishMessage();
  this.tree.writeln(text);
  this.state = NodeState.FINISHED;
  if (finishParentIfLast) {
    var parent = this.parent;
    if (parent != null && parent != this.tree.root) {
      parent.onChildFinished();
    }
  }
};

/**
 * @returns {boolean} if this node has been finished
 */
Node.prototype.isFinished = function () {
  return this.state === NodeState.FINISHED;
};

/**
 * @returns {string}
 * @private
 */
Node.prototype.getFinishMessage = function () {
  var text = '##teamcity[' + this.getFinishCommandName();
  text += ' nodeId=\'' + this.id + '\'';
  var extraParameters = this.getExtraFinishMessageParameters();
  if (extraParameters) {
    text += extraParameters;
  }
  text += ']';
  return text;
};

/**
 * @returns {string}
 * @abstract
 * @private
 */
Node.prototype.getExtraFinishMessageParameters = function () {
  throw Error('Must be implemented by subclasses');
};

Node.prototype.finishIfStarted = function () {
  if (this.state !== NodeState.FINISHED) {
    for (var i = 0; i < this.children.length; i++) {
      this.children[i].finishIfStarted();
    }
    this.finish();
  }
};

/**
 * TestSuiteNode child of Node class. Represents a non-leaf node without state (its state is computed by its child states).
 *
 * @param {Tree} tree test tree
 * @param {number} id this node's ID. It should be unique among all node IDs that belong to the same tree.
 * @param {TestSuiteNode} parent parent node
 * @param {String} name node name (e.g. config file name / browser name / suite name)
 * @param {String} type node type (e.g. 'config', 'browser')
 * @param {String} locationPath navigation info
 * @param {String} metaInfo
 * @constructor
 * @extends Node
 */
function TestSuiteNode(tree, id, parent, name, type, locationPath, metaInfo) {
  Node.call(this, tree, id, parent, name, type, locationPath, metaInfo);
  /**
   * @type {Array}
   * @public
   */
  this.children = [];
  /**
   * @type {Object}
   * @private
   */
  this.lookupMap = {};
  /**
   * @type {number}
   * @private
   */
  this.finishedChildCount = 0;
}

inherit(TestSuiteNode, Node);

/**
 * Returns child node by its name.
 * @param childName
 * @returns {?Node} child node (null, if no child node with such name found)
 */
TestSuiteNode.prototype.findChildNodeByName = function(childName) {
  if (Object.prototype.hasOwnProperty.call(this.lookupMap, childName)) {
    return this.lookupMap[childName];
  }
  return null;
};

/**
 * @returns {string}
 * @private
 */
TestSuiteNode.prototype.getStartCommandName = function () {
  return 'testSuiteStarted';
};

/**
 * @returns {string}
 * @private
 */
TestSuiteNode.prototype.getFinishCommandName = function () {
  return 'testSuiteFinished';
};

/**
 * @returns {string}
 * @private
 */
TestSuiteNode.prototype.getExtraFinishMessageParameters = function () {
  return null;
};

/**
 * Adds a new test child.
 * @param {string} childName node name (e.g. browser name / suite name / spec name)
 * @param {string} nodeType child node type (e.g. 'config', 'browser')
 * @param {string} locationPath navigation info
 * @returns {TestNode}
 */
TestSuiteNode.prototype.addTestChild = function (childName, nodeType, locationPath, metaInfo) {
  if (this.state === NodeState.FINISHED) {
    throw Error('Child node cannot be created for finished nodes!');
  }
  var childId = this.tree.nextId++;
  var child = new TestNode(this.tree, childId, this, childName, nodeType, locationPath, metaInfo);
  this.children.push(child);
  this.lookupMap[childName] = child;
  return child;
};

/**
 * Adds a new child for this suite node.
 * @param {String} childName node name (e.g. browser name / suite name / spec name)
 * @param {String} nodeType child node type (e.g. 'config', 'browser')
 * @param {String} locationPath navigation info
 * @param {String} metaInfo
 * @returns {TestSuiteNode}
 */
TestSuiteNode.prototype.addTestSuiteChild = function (childName, nodeType, locationPath, metaInfo) {
  if (this.state === NodeState.FINISHED) {
    throw Error('Child node cannot be created for finished nodes!');
  }
  var childId = this.tree.nextId++;
  var child = new TestSuiteNode(this.tree, childId, this, childName, nodeType, locationPath, metaInfo);
  this.children.push(child);
  this.lookupMap[childName] = child;
  return child;
};

/**
 * @protected
 */
TestSuiteNode.prototype.onChildFinished = function() {
  this.finishedChildCount++;
  if (this.finishedChildCount === this.children.length) {
    if (this.state !== NodeState.FINISHED) {
      this.finish(true);
    }
  }
};

/**
 * TestNode class that represents a test node.
 *
 * @param {Tree} tree test tree
 * @param {number} id this node ID. It should be unique among all node IDs that belong to the same tree.
 * @param {TestSuiteNode} parent parent node
 * @param {string} name node name (spec name)
 * @param {string} type node type (e.g. 'config', 'browser')
 * @param {string} locationPath navigation info
 * @constructor
 */
function TestNode(tree, id, parent, name, type, locationPath, metaInfo) {
  Node.call(this, tree, id, parent, name, type, locationPath, metaInfo);
  /**
   * @type {TestOutcome}
   * @private
   */
  this.outcome = undefined;
  /**
   * @type {number}
   * @private
   */
  this.durationMillis = undefined;
  /**
   * @type {string}
   * @private
   */
  this.failureMsg = undefined;
  /**
   * @type {string}
   * @private
   */
  this.failureDetails = undefined;
  /**
   * @type {string}
   * @private
   */
  this.expectedStr = undefined;
  /**
   * @type {string}
   * @private
   */
  this.actualStr = undefined;
  /**
   * @type {string}
   * @private
   */
  this.expectedFilePath = undefined;
  /**
   * @type {string}
   * @private
   */
  this.actualFilePath = undefined;
}

inherit(TestNode, Node);

/**
 * @param name
 * @constructor
 * @private
 */
function TestOutcome(name) {
  this.name = name;
}
TestOutcome.prototype.toString = function () {
    return this.name;
};

TestOutcome.SUCCESS = new TestOutcome("success");
TestOutcome.SKIPPED = new TestOutcome("skipped");
TestOutcome.FAILED = new TestOutcome("failed");
TestOutcome.ERROR = new TestOutcome("error");

Tree.TestOutcome = TestOutcome;

/**
 * @param {TestOutcome} outcome test outcome
 * @param {number} durationMillis test duration is ms
 * @param {string|null} failureMsg
 * @param {string|null} failureDetails
 * @param {string|null} expectedStr
 * @param {string|null} actualStr
 * @param {string|null} expectedFilePath
 * @param {string|null} actualFilePath
 * @public
 */
TestNode.prototype.setOutcome = function (outcome, durationMillis, failureMsg, failureDetails,
                                          expectedStr, actualStr,
                                          expectedFilePath, actualFilePath) {
  this.outcome = outcome;
  this.durationMillis = durationMillis;
  this.failureMsg = failureMsg;
  this.failureDetails = failureDetails;
  this.expectedStr = isString(expectedStr) ? expectedStr : null;
  this.actualStr = isString(actualStr) ? actualStr : null;
  this.expectedFilePath = isString(expectedFilePath) ? expectedFilePath : null;
  this.actualFilePath = isString(actualFilePath) ? actualFilePath : null;
  if (outcome === TestOutcome.SKIPPED && !failureMsg) {
    this.failureMsg = 'Pending test \'' + this.name + '\'';
  }
};

/**
 * @returns {string}
 * @private
 */
TestNode.prototype.getStartCommandName = function () {
  return 'testStarted';
};

/**
 * @returns {string}
 * @private
 */
TestNode.prototype.getFinishCommandName = function () {
  switch (this.outcome) {
    case TestOutcome.SUCCESS:
      return 'testFinished';
    case TestOutcome.SKIPPED:
      return 'testIgnored';
    case TestOutcome.FAILED:
      return 'testFailed';
    case TestOutcome.ERROR:
      return 'testFailed';
    default:
      throw Error('Unexpected outcome: ' + this.outcome);
  }
};

/**
 *
 * @returns {string}
 * @private
 */
TestNode.prototype.getExtraFinishMessageParameters = function () {
  var params = '';
  if (typeof this.durationMillis === 'number') {
    params += ' duration=\'' + this.durationMillis + '\'';
  }
  if (this.outcome === TestOutcome.ERROR) {
    params += ' error=\'yes\'';
  }
  if (isString(this.failureMsg)) {
    params += ' message=\'' + escapeAttributeValue(this.failureMsg) + '\'';
  }
  if (isString(this.failureDetails)) {
    params += ' details=\'' + escapeAttributeValue(this.failureDetails) + '\'';
  }
  if (isString(this.expectedStr)) {
    params += ' expected=\'' + escapeAttributeValue(this.expectedStr) + '\'';
  }
  if (isString(this.actualStr)) {
    params += ' actual=\'' + escapeAttributeValue(this.actualStr) + '\'';
  }
  if (isString(this.expectedFilePath)) {
    params += ' expectedFile=\'' + escapeAttributeValue(this.expectedFilePath) + '\'';
  }
  if (isString(this.actualFilePath)) {
    params += ' actualFile=\'' + escapeAttributeValue(this.actualFilePath) + '\'';
  }
  return params.length === 0 ? null : params;
};

/**
 * @param {string} err
 */
TestNode.prototype.addStdErr = function (err) {
  if (isString(err)) {
    var text = '##teamcity[testStdErr nodeId=\'' + this.id
      + '\' out=\'' + escapeAttributeValue(err) + '\']';
    this.tree.writeln(text);
  }
};




var hasOwnProperty = Object.prototype.hasOwnProperty;

function getRoot(suiteOrTest) {
  var node = suiteOrTest;
  while (!node.root) {
    node = node.parent;
  }
  return node;
}

function findRoot(runner) {
  if (runner.suite != null) {
    return getRoot(runner.suite)
  }
  if (runner.test != null) {
    return getRoot(runner.test)
  }
  return null;
}

function processTests(node, callback) {
  node.tests.forEach(function (test) {
    callback(test);
  });
  node.suites.forEach(function (suite) {
    processTests(suite, callback);
  });
}

function forEachTest(runner, callback) {
  var root = findRoot(runner);
  if (!root) {
    writeToStderr("[IDE integration] Cannot find mocha tree root node");
  }
  else {
    processTests(root, callback);
  }
}

function finishTree(tree) {
  tree.root.children.forEach(function (node) {
    node.finishIfStarted();
  });
}

var INTELLIJ_TEST_NODE = "intellij_test_node";
var INTELLIJ_SUITE_NODE = "intellij_suite_node";

/**
 * @param {Object} test mocha test
 * @returns {TestNode}
 */
function getNodeForTest(test) {
  if (hasOwnProperty.call(test, INTELLIJ_TEST_NODE)) {
    return test[INTELLIJ_TEST_NODE];
  }
  return null;
}

/**
 * @param {Object} test mocha test
 * @param {TestNode} testNode
 */
function setNodeForTest(test, testNode) {
  test[INTELLIJ_TEST_NODE] = testNode;
}

/**
 * @param {Object} suite mocha suite
 * @returns {TestSuiteNode}
 */
function getNodeForSuite(suite) {
  if (hasOwnProperty.call(suite, INTELLIJ_SUITE_NODE)) {
    return suite[INTELLIJ_SUITE_NODE];
  }
  return null;
}

/**
 * @param {Object} suite mocha suite
 * @param {TestSuiteNode} suiteNode
 */
function setNodeForSuite(suite, suiteNode) {
  suite[INTELLIJ_SUITE_NODE] = suiteNode;
}
/**
 * @param {*} value
 * @return {string}
 */
function stringify(value) {
  if (isString(value)) {
      return value;
  }
  var str = failoverStringify(value);
  if (isString(str)) {
    return str;
  }
  return 'Oops, something went wrong: IDE failed to stringify ' + typeof value;
}

/**
 * @param {*} value
 * @return {string}
 */
function failoverStringify(value) {
  var normalizedValue = deepCopyAndNormalize(value);
  if (normalizedValue instanceof RegExp) {
    return normalizedValue.toString();
  }
  if (normalizedValue === undefined) {
    return 'undefined';
  }
  return JSON.stringify(normalizedValue, null, 2);
}

function isObject(val) {
  return val === Object(val);
}

function deepCopyAndNormalize(value) {
  var cache = [];
  return (function doCopy(value) {
    if (value == null) {
      return value;
    }
    if (typeof value === 'number' || typeof value === 'boolean' || typeof value === 'string') {
      return value;
    }
    if (value instanceof RegExp) {
      return value;
    }

    if (cache.indexOf(value) !== -1) {
      return '[Circular reference found] Truncated by IDE';
    }
    cache.push(value);

    if (Array.isArray(value)) {
      return value.map(function (element) {
        return doCopy(element);
      });
    }

    if (isObject(value)) {
      var keys = Object.keys(value);
      keys.sort();
      var ret = {};
      keys.forEach(function (key) {
        ret[key] = doCopy(value[key]);
      });
      return ret;
    }

    return value;
  })(value);
}

/**
 * @constructor
 * @param {Function} processor
 */
function SingleElementQueue(processor) {
  this.processor = processor;
  this.current = null;
}

SingleElementQueue.prototype.add = function (element) {
  if (this.current != null) {
    process.stderr.write("mocha-intellij: unexpectedly unprocessed element " + element);
    this.processor(this.current);
  }
  this.current = element;
};

SingleElementQueue.prototype.processAll = function () {
  if (this.current != null) {
    this.processor(this.current);
    this.current = null;
  }
};

SingleElementQueue.prototype.clear = function () {
  this.current = null;
};


// Reference: http://es5.github.io/#x15.4.4.19
if (!Array.prototype.map) {
  Array.prototype.map = function(callback/*, thisArg*/) {
    var T, A, k;

    if (this == null) {
      throw new TypeError('this is null or not defined');
    }
    var O = Object(this);
    var len = O.length >>> 0;

    if (typeof callback !== 'function') {
      throw new TypeError(callback + ' is not a function');
    }
    if (arguments.length > 1) {
      T = arguments[1];
    }
    A = new Array(len);
    k = 0;

    while (k < len) {
      var kValue, mappedValue;
      if (k in O) {
        kValue = O[k];
        mappedValue = callback.call(T, kValue, k, O);
        A[k] = mappedValue;
      }
      k++;
    }
    return A;
  };
}

/**
 * @param {Tree} tree
 * @param test mocha test object
 * @returns {TestSuiteNode}
 */
function findOrCreateAndRegisterSuiteNode(tree, test) {
  var suites = getSuitesFromRootDownTo(test.parent);
  var parentNode = tree.root, suiteId;
  for (suiteId = 0; suiteId < suites.length; suiteId++) {
    var suite = suites[suiteId];
    var suiteName = suite.title;
    var childNode = getNodeForSuite(suite);
    if (!childNode) {
      var locationPath = getLocationPath(parentNode, suiteName);
      childNode = parentNode.addTestSuiteChild(suiteName, 'suite', locationPath, test.file);
      childNode.register();
      setNodeForSuite(suite, childNode);
    }
    parentNode = childNode;
  }
  return parentNode;
}

function getSuitesFromRootDownTo(suite) {
  var suites = [];
  var s = suite;
  while (s != null && !s.root) {
    suites.push(s);
    s = s.parent;
  }
  suites.reverse();
  return suites;
}

/**
 * @param {TestSuiteNode} parent
 * @param {string} childName
 * @returns {string}
 */
function getLocationPath(parent, childName) {
  var names = []
    , node = parent
    , root = node.tree.root;
  while (node !== root) {
    names.push(node.name);
    node = node.parent;
  }
  names.reverse();
  names.push(childName);
  return joinList(names, 0, names.length, '.');
}

function extractErrInfo(err) {
  var message = err.message || ''
    , stack = err.stack;
  if (!isString(stack) || stack.trim().length == 0) {
    return {
      message: message
    }
  }
  var index = stack.indexOf(message);
  if (index >= 0) {
    message = stack.slice(0, index + message.length);
    stack = stack.slice(message.length);
    var nl = '\n';
    if (stack.indexOf(nl) === 0) {
      stack = stack.substring(nl.length);
    }
  }
  return {
    message : message,
    stack : stack
  }
}

/**
 * @param {Tree} tree
 * @param test mocha test object
 * @returns {TestNode}
 */
function registerTestNode(tree, test) {
  var testNode = getNodeForTest(test);
  if (testNode != null) {
    throw Error("Test node has already been associated!");
  }
  var suiteNode = findOrCreateAndRegisterSuiteNode(tree, test);
  var locationPath = getLocationPath(suiteNode, test.title);
  testNode = suiteNode.addTestChild(test.title, 'test', locationPath, test.file);
  testNode.register();
  setNodeForTest(test, testNode);
  return testNode;
}

/**
 * @param {Tree} tree
 * @param test mocha test object
 * @returns {TestNode}
 */
function startTest(tree, test) {
  var testNode = getNodeForTest(test);
  if (testNode == null) {
    testNode = registerTestNode(tree, test);
  }
  testNode.start();
  return testNode;
}

/**
 *
 * @param {TestNode} testNode
 * @param {*} err
 */
function addStdErr(testNode, err) {
  if (err != null) {
    if (isString(err)) {
      testNode.addStdErr(err);
    }
    else {
      var errInfo = extractErrInfo(err);
      if (errInfo != null) {
        var out = errInfo.message || errInfo.stack;
        if (errInfo.message && errInfo.stack) {
          out = errInfo.message + '\n' + errInfo.stack;
        }
        testNode.addStdErr(out);
      }
    }
  }
}

/**
 * @param {Tree} tree
 * @param {Object} test mocha test object
 * @param {Object} err mocha error object
 * @param {SingleElementQueue} [finishingQueue]
 */
function finishTestNode(tree, test, err, finishingQueue) {
  var testNode = getNodeForTest(test);
  if (finishingQueue != null) {
    const passed = testNode != null && testNode === finishingQueue.current && testNode.outcome === Tree.TestOutcome.SUCCESS;
    if (passed && err != null) {
      // do not deliver passed event if this test is failed now
      finishingQueue.clear();
    }
    else {
      finishingQueue.processAll();
    }
  }

  if (testNode != null && testNode.isFinished()) {
    /* See https://youtrack.jetbrains.com/issue/WEB-10637
       A test can be reported as failed and passed at the same test run if a error is raised using
         this.test.error(new Error(...));
       At least all errors should be presented to a user. */
    addStdErr(testNode, err);
    return;
  }
  testNode = startTest(tree, test);
  if (err) {
    var expected = getOwnProperty(err, 'expected');
    var actual = getOwnProperty(err, 'actual');
    var expectedStr = null, actualStr = null;
    if (err.showDiff !== false && expected !== actual && expected !== undefined) {
      if (isStringPrimitive(expected) && isStringPrimitive(actual)) {
        // in compliance with mocha's own behavior
        //   https://github.com/mochajs/mocha/blob/v3.0.2/lib/reporters/base.js#L204
        //   https://github.com/mochajs/mocha/commit/d55221bc967f62d1d8dd4cd8ce4c550c15eba57f
        expectedStr = expected.toString();
        actualStr = actual.toString();
      }
      else {
        expectedStr = stringify(expected);
        actualStr = stringify(actual);
      }
    }
    var errInfo = extractErrInfo(err);
    testNode.setOutcome(Tree.TestOutcome.FAILED, test.duration, errInfo.message, errInfo.stack,
                        expectedStr, actualStr,
                        getOwnProperty(err, 'expectedFilePath'), getOwnProperty(err, 'actualFilePath'));
  }
  else {
    var status = test.pending ? Tree.TestOutcome.SKIPPED : Tree.TestOutcome.SUCCESS;
    testNode.setOutcome(status, test.duration, null, null, null, null, null, null);
  }
  if (finishingQueue != null) {
    finishingQueue.add(testNode);
  }
  else {
    testNode.finish(false);
  }
}

/**
 * @param {object} obj javascript object
 * @param {string} key object own key to retrieve
 * @return {*}
 */
function getOwnProperty(obj, key) {
  var value;
  if (Object.prototype.hasOwnProperty.call(obj, key)) {
    value = obj[key];
  }
  return value;
}

/**
 * @param {Object} test mocha test object
 * @return {boolean}
 */
function isHook(test) {
  return test.type === 'hook';
}

/**
 * @param {Object} test mocha test object
 * @return {boolean}
 */
function isBeforeAllHook(test) {
  return isHook(test) && test.title && test.title.indexOf('"before all" hook') === 0;
}

/**
 * @param {Object} test mocha test object
 * @return {boolean}
 */
function isBeforeEachHook(test) {
  return isHook(test) && test.title && test.title.indexOf('"before each" hook') === 0;
}

/**
 * @param {Tree} tree
 * @param {Object} suite mocha suite
 * @param {string} cause
 */
function markChildrenFailed(tree, suite, cause) {
  suite.tests.forEach(function (test) {
    var testNode = getNodeForTest(test);
    if (testNode != null) {
      finishTestNode(tree, test, {message: cause});
    }
  });
}

function getCurrentTest(ctx) {
  return ctx != null ? ctx.currentTest : null;
}

function handleBeforeEachHookFailure(tree, beforeEachHook, err) {
  var done = false;
  var currentTest = getCurrentTest(beforeEachHook.ctx);
  if (currentTest != null) {
    var testNode = getNodeForTest(currentTest);
    if (testNode != null) {
      finishTestNode(tree, currentTest, err);
      done = true;
    }
  }
  if (!done) {
    finishTestNode(tree, beforeEachHook, err);
  }
}

/**
 * @param {Object} suite mocha suite object
 */
function finishSuite(suite) {
  var suiteNode = getNodeForSuite(suite);
  if (suiteNode == null) {
    throw Error('Cannot find suite node for ' + suite.title);
  }
  suiteNode.finish(false);
}

// const BaseReporter = requireBaseReporter();
// if (BaseReporter) {
//     require('util').inherits(IntellijReporter, BaseReporter);
// }

function IntellijReporter(runner) {
  // if (BaseReporter) {
  //   BaseReporter.call(this, runner);
  // }
  var tree;
  // allows to postpone sending test finished event until 'afterEach' is done
  var finishingQueue = new SingleElementQueue(function (testNode) {
    testNode.finish(false);
  });
  let curId = undefined
  runner.on('start', safeFn(function () {
    if (tree && tree.nextId > 0) {
      curId = tree.nextId
    }
    tree = new Tree(function (str) {
      writeToStdout(str);
    });
    if (curId) {
      tree.nextId = curId
    }

    // tree.writeln('##teamcity[enteredTheMatrix]');
    // tree.testingStarted();

    var tests = [];
    forEachTest(runner, function (test) {
      var match = true;
      if (runner._grep instanceof RegExp) {
        match = runner._grep.test(test.fullTitle());
      }
      if (match) {
        tests.push(test);
      }
    });

    tree.writeln('##teamcity[testCount count=\'' + tests.length + '\']');
    tests.forEach(function (test) {
      registerTestNode(tree, test);
    });
  }));

  runner.on('suite', safeFn(function (suite) {
    var suiteNode = getNodeForSuite(suite);
    if (suiteNode != null) {
      suiteNode.start();
    }
  }));

  runner.on('test', safeFn(function (test) {
    finishingQueue.processAll();
    startTest(tree, test);
  }));

  runner.on('pending', safeFn(function (test) {
    finishingQueue.processAll();
    finishTestNode(tree, test, null, finishingQueue);
  }));

  runner.on('pass', safeFn(function (test) {
    finishTestNode(tree, test, null, finishingQueue);
  }));

  runner.on('fail', safeFn(function (test, err) {
    if (isBeforeEachHook(test)) {
      finishingQueue.processAll();
      handleBeforeEachHookFailure(tree, test, err);
    }
    else if (isBeforeAllHook(test)) {
      finishingQueue.processAll();
      finishTestNode(tree, test, err);
      markChildrenFailed(tree, test.parent, test.title + " failed");
    }
    else {
      finishTestNode(tree, test, err, finishingQueue);
    }
  }));

  runner.on('suite end', safeFn(function (suite) {
    finishingQueue.processAll();
    if (!suite.root) {
      finishSuite(suite);
    }
  }));

  runner.on('end', safeFn(function () {
    finishingQueue.processAll();
    tree.testingFinished();
    tree = null;
  }));

}

module.exports = IntellijReporter;
