// vertical-rl scroll coordinates differ by engine convention: modern WebKit
// uses a negative scrollLeft range (0 at the right-edge reading start, -max at
// the end); older engines use 0..max with max at the start. These helpers
// normalize everything to "distance from the reading start".
var __ebDoc = document.scrollingElement || document.documentElement;
function __ebMax() { return Math.max(0, __ebDoc.scrollWidth - __ebDoc.clientWidth); }
function __ebNegRange() {
    var o = __ebDoc.scrollLeft;
    __ebDoc.scrollLeft = -1;
    var neg = __ebDoc.scrollLeft < 0;
    __ebDoc.scrollLeft = o;
    return neg;
}
function __ebFromStart() {
    return __ebNegRange() ? -__ebDoc.scrollLeft : (__ebMax() - __ebDoc.scrollLeft);
}
function __ebSetFromStart(v) {
    __ebDoc.scrollLeft = __ebNegRange() ? -v : (__ebMax() - v);
}
