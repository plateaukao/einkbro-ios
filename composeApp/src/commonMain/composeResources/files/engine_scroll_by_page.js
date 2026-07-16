// Engine-level fallback paging: scroll by ~one viewport height.
// __SIGN__ is -1 for page up, 1 for page down.
window.scrollBy({top: __SIGN__ * window.innerHeight * 0.92, left: 0, behavior: 'instant'});
