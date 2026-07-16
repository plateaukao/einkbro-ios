// Restores the pre-translation DOM (translate_by_paragraph.js saved it in
// document.originalInnerHTML) and clears the in-place flag.
(function() {
    if (document.originalInnerHTML) {
        document.body.innerHTML = document.originalInnerHTML;
    }
    document.body.classList.remove("translated");
    window._translateInPlace = false;
})();
