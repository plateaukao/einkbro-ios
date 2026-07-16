// Returns the page's visible text (used for read-aloud and GPT summarize).
(function() {
    return document.body ? document.body.innerText : "";
})();
