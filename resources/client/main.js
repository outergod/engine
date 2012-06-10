require(['ace/ace'], function (ace) {
  return require(['jquery', 'ace/edit_session', 'ace/range', 'ace/commands/command_manager', 'theme/engine', 'socket.io/socket.io'], function ($, edit, range, command) {
    var editor = ace.edit('editor'),
        renderer = editor.renderer,
        session = edit.EditSession,
        responder = function (editor) {
          return function (response) {
            if (Object.prototype.toString.call (response) !== '[object Array]') {
              response = [response];
            }

            response.forEach(function (value) {
              editor.commands.exec(value.command, { editor: editor }, value.args);
            });
          };
        };

    editor.io = io.connect();
    editor.bufferName = '*scratch*';
    renderer.setShowGutter(false);
    renderer.setShowPrintMargin(false);
    renderer.setHScrollBarAlwaysVisible(false);
    editor.setTheme('theme/engine');
    editor.setFontSize('13px');
    editor.setKeyboardHandler({ handleKeyboard: function (data, hashId, textOrKey, keyCode, e) {
      //console.log('got ' + hashId + ' [' + textOrKey + '] ' + keyCode);

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
      name: 'insert-text', exec: function (env, args) {
        env.editor.session.insert(args.position, args.text);
      }
    }, {
      name: 'move-to-position', exec: function (env, args) {
        env.editor.moveCursorTo(args.row, args.column);
      }
    }, {
      name: 'delete-range', exec: function (env, args) {
        env.editor.session.remove(range.Range.fromPoints.apply(null, args));
      }
    }, {
      name: 'execute-extended-command', exec: function () {
        //$('#meta').empty().append('<input id="engine-minibuffer" type="text"/>');
        //$('#content').append('<div id="gcli-display"/>').wrapInner('<div class="hbox"/>');
      }
    }]);

    ['mousedown', 'dblclick', 'tripleclick', 'quadclick'].forEach (function (el) {
      editor.on(el, function (e) {
        if (e.type == 'mousedown') {
          e.editor.io.emit('mouse', e.type, e.getButton(), e.getDocumentPosition(), editor.bufferName, responder(e.editor));
        }
        return e.preventDefault();
      });
    });

    editor.io.emit('load-buffer', '*scratch*', function (contents, position) {
      editor.setSession(new session(contents));
      editor.moveCursorTo(position.row, position.column);
    });
  });
});
