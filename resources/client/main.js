// -*- mode: js2; indent-tabs-mode: nil; -*-
require(['ace/ace', 'theme/engine'], function () {
  return require(['jquery', 'engine/window', 'engine/socket.io'],
         function ($,        window,          io) {
    var socket = io.connect(), editor, minibuffer;

    socket.on('broadcast', function (command) {
      var editor = window.instances[command.args.buffer];
      if (editor) {
        editor.commands.exec(command.command, { editor: editor }, command.args);
      }
    });

    editor = window.create({
      element: 'editor',
      io: socket,
      bufferName: '*scratch*',
      theme: 'theme/engine',
      fontSize: '13px'
    });

    minibuffer = window.create({
      element: 'minibuffer',
      io: socket,
      bufferName: '*minibuffer*',
      theme: 'theme/engine',
      fontSize: '13px'
    });

    editor.focus();
    $(minibuffer.container).parent().height(minibuffer.renderer.lineHeight);
  });
});
