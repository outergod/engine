require(['ace/ace'], function (ace) {
  return require(['ace/edit_session', 'ace/commands/command_manager', 'theme/engine', 'socket.io/socket.io'], function (edit, command) {
    var editor = ace.edit('editor'),
        renderer = editor.renderer,
        session = edit.EditSession,
        responder = function (editor) {
          return function (response) {
            editor.commands.exec(response.command, { editor: editor }, response.args);
          };
        };

    editor.io = io.connect();
    editor.bufferName = '*scratch*';
    renderer.setShowGutter(false);
    renderer.setShowPrintMargin(false);
    editor.setTheme('theme/engine');
    editor.setKeyboardHandler({ handleKeyboard: function (data, hashId, textOrKey, keyCode, e) {
      console.log('got ' + hashId + ' [' + textOrKey + '] ' + keyCode);

      if (keyCode !== undefined) {
        editor.io.emit('keyboard', hashId, textOrKey, keyCode, editor.bufferName, responder(editor));
      }

      return hashId === 0 || hashId === 4 ? {} : { command: 'noop' }; // noop prevents propagation of e.g. ctrl+r; empty object return still propagates event -> insertstring
    }});

    editor.commands = new command.CommandManager('win', [{
      name: 'noop', exec: function () {} // only here to stop event propagation, see above
    }, {
      name: 'insertstring', exec: function (editor, args) {
        editor.io.emit('keyboard', 0, args, undefined, editor.bufferName, responder(editor));
      }
    }, {
      name: 'self-insert-command', exec: function (env, args) {
        env.editor.insert(args.text);
      }
    }, {
      name: 'backward-char', exec: function (env, args) {
        env.editor.navigateLeft(1);
      }
    }, {
      name: 'forward-char', exec: function (env, args) {
        env.editor.navigateRight(1);
      }
    }, {
      name: 'backward-delete-char', exec: function (env, args) {
        env.editor.remove('left');
      }
    }, {
      name: 'delete-char', exec: function (env, args) {
        env.editor.remove('right');
      }
    }, {
      name: 'move-to-position', exec: function (env, args) {
        env.editor.moveCursorTo(args.row, args.column);
      }
    }]);

    editor.io.emit('load-buffer', '*scratch*', function (contents, position) {
      editor.setSession(new session(contents));
      editor.moveCursorTo(position.row, position.column);
    });
  });
});
