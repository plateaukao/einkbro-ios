(function () {
    var nodes = window.__ebTocNodes || [];
    var n = nodes[__INDEX__];
    // block/inline both 'start' so it works in horizontal and vertical-rl modes.
    if (n) n.scrollIntoView({ block: 'start', inline: 'start' });
})();
