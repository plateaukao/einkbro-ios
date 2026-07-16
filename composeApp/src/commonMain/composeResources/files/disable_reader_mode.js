// Restores the pre-reader DOM cached by replace_reader_body.js.
(function() {
    document.body.innerHTML = document.innerHTMLCache;
    document.body.classList.remove("mozac-readerview-body");
    window.scrollTo(0, 0);
})();
