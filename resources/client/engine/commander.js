// -*- mode: js; indent-tabs-mode: nil; -*-
define (['jquery', 'ace/commands/command_manager', 'ace/range'],
function ($,        command,                        range) {
  var defaults = { 
    commands: {
      'noop': function () {}, // only here to stop event propagation
      'insert-text': function (env, args) {
        env.editor.session.insert(args.position, args.text);
      },
      'move-to-position': function (env, args) {
        env.editor.moveCursorTo(args.row, args.column);
      },
      'delete-range': function (env, args) {
        env.editor.session.remove(range.Range.fromPoints.apply(null, args.range));
      },
      'clear': function (env) {
        env.editor.clear();
      }
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
