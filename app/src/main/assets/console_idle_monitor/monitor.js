(function () {
  "use strict";

  var installedKey = "__lexiaShellConsoleIdleMonitorInstalled";
  var methods = ["log", "info", "warn", "error", "debug"];

  function reportConsoleOutput(method) {
    try {
      var result = browser.runtime.sendNativeMessage("browser", {
        type: "console-output",
        method: method
      });
      if (result && typeof result.catch === "function") {
        result.catch(function () {});
      }
    } catch (error) {
      // Startup reveal has a no-console fallback if native messaging is unavailable.
    }
  }

  function wrapConsole(targetWindow, exportToTarget) {
    var targetConsole = targetWindow.console;
    if (!targetConsole || targetWindow[installedKey]) {
      return;
    }

    targetWindow[installedKey] = true;
    methods.forEach(function (method) {
      var original = targetConsole[method];
      if (typeof original !== "function") {
        return;
      }

      var wrapped = function () {
        reportConsoleOutput(method);
        return original.apply(this, arguments);
      };

      targetConsole[method] = exportToTarget ? exportFunction(wrapped, targetWindow) : wrapped;
    });
  }

  try {
    if (window.wrappedJSObject && typeof exportFunction === "function") {
      wrapConsole(window.wrappedJSObject, true);
      return;
    }
  } catch (error) {
    // Fall through to the content-script world.
  }

  wrapConsole(window, false);
})();
