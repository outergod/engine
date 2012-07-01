// -*- mode: js; indent-tabs-mode: nil; -*-
define (['jquery', 'ace/edit_session'],
function ($,        session) {
  return {
    create: function (spec, my) {
      var that = new session.EditSession(spec.contents);
      return that;
    }
  };
});
