import { jsx as _jsx, jsxs as _jsxs } from "react/jsx-runtime";
import './QueuePanel.css';
export function QueuePanel({ tracks, onRemove }) {
    if (tracks.length === 0) {
        return (_jsxs("section", { className: "queue", children: [_jsx("h2", { children: "Your queue" }), _jsx("p", { className: "queue__empty", children: "Add tracks you like and turn them into a playlist." })] }));
    }
    return (_jsxs("section", { className: "queue", children: [_jsxs("div", { className: "queue__header", children: [_jsx("h2", { children: "Your queue" }), _jsxs("span", { children: [tracks.length, " track", tracks.length === 1 ? '' : 's'] })] }), _jsx("ul", { children: tracks.map((track) => (_jsxs("li", { children: [_jsxs("div", { children: [_jsx("p", { className: "queue__track-name", children: track.name }), _jsx("p", { className: "queue__track-artist", children: track.artist })] }), _jsx("button", { onClick: () => onRemove(track.spotifyId), "aria-label": `Remove ${track.name}`, children: "\u00D7" })] }, track.spotifyId))) })] }));
}
