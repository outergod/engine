/* Engine - engine.js Ace Engine theme definition
 * Copyright (C) 2012  Alexander Kahl <e-user@fsfe.org>
 * This file is part of Engine.
 * Engine is free software; you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation; either version 3 of the License, or
 * (at your option) any later version.
 *
 * Engine is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 *
 * The Original Code is Ajax.org Code Editor (ACE).
 *
 * The Initial Developer of the Original Code is
 * Ajax.org B.V.
 * Portions created by the Initial Developer are Copyright (C) 2010
 * the Initial Developer. All Rights Reserved.
 *
 * Contributor(s):
 *      Fabian Jakobs <fabian AT ajax DOT org>
 */

define(['ace/lib/dom'], function (dom) {
  var style = { isDark: true,
                cssClass: 'ace-engine',
                cssText: "@import url(client/Inconsolata.css);\
\
.ace-engine > * {\
font-family: 'Inconsolata';\
}\
\
.ace-engine .ace_editor {\
border: 2px solid rgb(159, 159, 159);\
}\
\
.ace-engine .ace_editor.ace_focus {\
border: 2px solid #327fbd;\
}\
\
.ace-engine .ace_gutter {\
width: 50px;\
background: #e8e8e8;\
color: #333;\
overflow : hidden;\
}\
\
.ace-engine .ace_gutter-layer {\
width: 100%;\
text-align: right;\
}\
\
.ace-engine .ace_gutter-layer .ace_gutter-cell {\
padding-right: 6px;\
}\
\
.ace-engine .ace_print_margin {\
width: 1px;\
background: #e8e8e8;\
}\
.ace-engine .ace_scroller {\
background: black;\
}\
\
.ace-engine .ace_text-layer {\
cursor: text;\
color: white;\
text-shadow: 0 0 5px white, 0 0 10px white;\
}\
\
.ace-engine .ace_cursor {\
opacity: 0.2;\
background: lightgray;\
}\
\
.ace-engine .ace_cursor.ace_hidden {\
background: none;\
}\
\
.ace-engine .ace_cursor.ace_overwrite {\
}\
\
.ace-engine .ace_line .ace_invisible {\
color: rgb(191, 191, 191);\
}\
\
.ace-engine .ace_line .ace_keyword {\
color: blue;\
}\
\
.ace-engine .ace_line .ace_constant.ace_buildin {\
color: rgb(88, 72, 246);\
}\
\
.ace-engine .ace_line .ace_constant.ace_language {\
color: rgb(88, 92, 246);\
}\
\
.ace-engine .ace_line .ace_constant.ace_library {\
color: rgb(6, 150, 14);\
}\
\
.ace-engine .ace_line .ace_invalid {\
background-color: rgb(153, 0, 0);\
color: white;\
}\
\
.ace-engine .ace_line .ace_fold {\
outline-color: #1C00FF;\
}\
\
.ace-engine .ace_line .ace_support.ace_function {\
color: rgb(60, 76, 114);\
}\
\
.ace-engine .ace_line .ace_support.ace_constant {\
color: rgb(6, 150, 14);\
}\
\
.ace-engine .ace_line .ace_support.ace_type,\
.ace-engine .ace_line .ace_support.ace_class {\
color: rgb(109, 121, 222);\
}\
\
.ace-engine .ace_line .ace_keyword.ace_operator {\
color: rgb(104, 118, 135);\
}\
\
.ace-engine .ace_line .ace_string {\
color: rgb(3, 106, 7);\
}\
\
.ace-engine .ace_line .ace_comment {\
color: rgb(76, 136, 107);\
}\
\
.ace-engine .ace_line .ace_comment.ace_doc {\
color: rgb(0, 102, 255);\
}\
\
.ace-engine .ace_line .ace_comment.ace_doc.ace_tag {\
color: rgb(128, 159, 191);\
}\
\
.ace-engine .ace_line .ace_constant.ace_numeric {\
color: rgb(0, 0, 205);\
}\
\
.ace-engine .ace_line .ace_variable {\
color: rgb(49, 132, 149);\
}\
\
.ace-engine .ace_line .ace_xml_pe {\
color: rgb(104, 104, 91);\
}\
\
.ace-engine .ace_entity.ace_name.ace_function {\
color: #0000A2;\
}\
\
.ace-engine .ace_markup.ace_markupine {\
text-decoration:underline;\
}\
\
.ace-engine .ace_markup.ace_heading {\
color: rgb(12, 7, 255);\
}\
\
.ace-engine .ace_markup.ace_list {\
color:rgb(185, 6, 144);\
}\
\
.ace-engine .ace_marker-layer .ace_selection {\
background: rgb(181, 213, 255);\
}\
\
.ace-engine .ace_marker-layer .ace_step {\
background: rgb(252, 255, 0);\
}\
\
.ace-engine .ace_marker-layer .ace_stack {\
background: rgb(164, 229, 101);\
}\
\
.ace-engine .ace_marker-layer .ace_bracket {\
margin: -1px 0 0 -1px;\
border: 1px solid rgb(192, 192, 192);\
}\
\
.ace-engine .ace_marker-layer .ace_active_line {\
background: rgba(0, 0, 0, 0.07);\
}\
\
.ace-engine .ace_marker-layer .ace_selected_word {\
background: rgb(250, 250, 255);\
border: 1px solid rgb(200, 200, 250);\
}\
\
.ace-engine .ace_meta.ace_tag {\
color:rgb(28, 2, 255);\
}\
\
.ace-engine .ace_string.ace_regex {\
color: rgb(255, 0, 0)\
}"
  };

  dom.importCssString(style.cssText);
  return style;
});
