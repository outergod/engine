// -*- mode: js; indent-tabs-mode: nil; -*-
define (['jquery', 'ace/commands/command_manager', 'ace/range'],
function ($,        command,                        range) {
  var defaults = { 
    commands: {
      'noop': function () {} // only here to stop event propagation
    }
  };

  return {
    defaults: defaults,
    create: function (spec) {
      var that = new command.CommandManager('win'), commands = $.extend({}, defaults.commands, spec);
      that.addCommands(commands);

      return that;
    }
  };
});
