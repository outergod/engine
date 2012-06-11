// -*- mode: js2; indent-tabs-mode: nil; -*-
require(['ace/ace', 'theme/engine'], function () {
  return require(['engine/window', 'engine/socket.io', 'ace/lib/event'], function (window, io, event) {
    var connection = io.connect(), editor, minibuffer;

    editor = window({
      element: 'editor',
      io: connection,
      bufferName: '*scratch*',
      theme: 'theme/engine',
      fontSize: '13px'
    });

    minibuffer = window({
      element: 'minibuffer',
      io: connection,
      bufferName: '*minibuffer*',
      theme: 'theme/engine',
      fontSize: '13px'
    });

    editor.focus();
  });
});
