// -*- mode: js2; indent-tabs-mode: nil; -*-
define (['ace/ace', 'jquery', 'ace/edit_session', 'ace/range', 'ace/commands/command_manager'],
function (ace,       $,        edit,               range,       command) {
  return function (spec) {
      var that = ace.edit(spec.element),
          renderer = that.renderer,
          Session = edit.EditSession,
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

      renderer.setShowGutter(false);
      renderer.setShowPrintMargin(false);
      renderer.setHScrollBarAlwaysVisible(false);

      that.io = spec.io;
      that.bufferName = spec.bufferName;
      that.setTheme(spec.theme);
      that.setFontSize(spec.fontSize);

      that.setKeyboardHandler({ handleKeyboard: function (data, hashId, textOrKey, keyCode, e) {
        //console.log('got ' + hashId + ' [' + textOrKey + '] ' + keyCode);

        if (keyCode !== undefined) {
          that.io.emit('keyboard', hashId, textOrKey, keyCode, that.bufferName, responder(that));
        }

        return hashId === 0 || hashId === 4 ? {} : { command: 'noop' }; // noop prevents propagation of e.g. ctrl+r; empty object return still propagates event -> insertstring
      }});

      that.commands = new command.CommandManager('win', [{
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
          // FIXME
        }
      }]);

      ['mousedown', 'dblclick', 'tripleclick', 'quadclick'].forEach (function (ev) {
        that.on(ev, function (e) {
          if (e.type == 'mousedown') {
            e.editor.io.emit('mouse', e.type, e.getButton(), e.getDocumentPosition(), that.bufferName, responder(e.editor));
          }
          return e.preventDefault();
        });
      });

      that.io.emit('load-buffer', that.bufferName, function (contents, position) {
        that.setSession(new Session(contents));
        that.moveCursorTo(position.row, position.column);
      });

      return that;
  };
});
