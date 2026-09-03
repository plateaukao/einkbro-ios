// Document-start counterpart of update_css_slot.js for the "main" style slot:
// installed as a WKUserScript so the page's first layout already uses the
// configured fonts/size instead of restyling after load. Uses the same element
// id, so the page-finished updateCssSlot finds the slot populated and skips
// the DOM mutation when nothing changed. At document start <head> may not
// exist yet; a <style> appended to <html> applies all the same.
(function () {
    var id = 'einkbro-css-main';
    var css = '';
    try {
        css = decodeURIComponent(escape(window.atob('__CSS_B64__')));
    } catch (e) {
        return;
    }
    if (!css || document.getElementById(id)) return;
    var el = document.createElement('style');
    el.id = id;
    el.type = 'text/css';
    el.textContent = css;
    (document.head || document.documentElement).appendChild(el);
})();
