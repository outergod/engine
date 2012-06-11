// -*- mode: js2; indent-tabs-mode: nil; -*-
require(['ace/ace', 'theme/engine'], function () {
  return require(['engine/window', 'engine/socket.io'], function (window, io) {
    window({
      element: 'editor',
      io: io.connect(),
      bufferName: '*scratch*',
      theme: 'theme/engine',
      fontSize: '13px'
    });
  });
});
