import { useState, useRef, useEffect } from "react";
import Image from "next/image";
import { FaCheck, FaSearch, FaSpinner } from "react-icons/fa";

export default function SearchForm({ onSearch }) {
  const [name1, setName1] = useState("");
  const [name2, setName2] = useState("");
  const [selectedId1, setSelectedId1] = useState("");
  const [selectedId2, setSelectedId2] = useState("");
  const [suggestions1, setSuggestions1] = useState([]);
  const [suggestions2, setSuggestions2] = useState([]);
  const [showSuggestions1, setShowSuggestions1] = useState(false);
  const [showSuggestions2, setShowSuggestions2] = useState(false);
  const [isSearching1, setIsSearching1] = useState(false);
  const [isSearching2, setIsSearching2] = useState(false);
  const [showSearchButton1, setShowSearchButton1] = useState(true);
  const [showSearchButton2, setShowSearchButton2] = useState(true);
  const input1Ref = useRef(null);
  const input2Ref = useRef(null);
  const hideTimeout1Ref = useRef(null);
  const hideTimeout2Ref = useRef(null);
  const searchTimeout1 = useRef(null);
  const searchTimeout2 = useRef(null);
  const abortController1 = useRef(null);
  const abortController2 = useRef(null);
  // simple in-memory cache for suggestions (TTL 60s, max 100 entries)
  const cacheRef = useRef(new Map());
  const CACHE_STORAGE_KEY = 'celebritySuggestionsCacheV1';
  const CACHE_TTL_MS = 60000;
  const MAX_CACHE_ENTRIES = 100;

  // Persist cache to localStorage so both inputs benefit across reloads
  const persistCache = () => {
    try {
      // Serialize as array of [key, value]
      const entries = Array.from(cacheRef.current.entries());
      if (typeof window !== 'undefined') {
        window.localStorage.setItem(CACHE_STORAGE_KEY, JSON.stringify(entries));
      }
    } catch (_) {
      // ignore storage errors
    }
  };

  // Hydrate cache from localStorage on mount
  useEffect(() => {
    try {
      if (typeof window === 'undefined') return;
      const raw = window.localStorage.getItem(CACHE_STORAGE_KEY);
      if (!raw) return;
      const parsed = JSON.parse(raw);
      if (!Array.isArray(parsed)) return;
      const now = Date.now();
      const hydrated = new Map();
      for (const [key, value] of parsed) {
        if (value && typeof value === 'object' && Array.isArray(value.data) && typeof value.t === 'number') {
          // Respect TTL (60s)
          if (now - value.t < CACHE_TTL_MS) {
            hydrated.set(key, value);
          }
        }
      }
      cacheRef.current = hydrated;
    } catch (_) {
      // ignore parse/storage errors
    }
  }, []);

  // Removed unused photo cache/getter; SuggestionItem handles its own photo fetching

  // Cleanup cache to prevent memory leaks
  const cleanupCache = () => {
    const now = Date.now();
    const maxEntries = MAX_CACHE_ENTRIES;
    
    // Remove expired entries
    for (const [key, value] of cacheRef.current.entries()) {
      if (now - value.t > CACHE_TTL_MS) {
        cacheRef.current.delete(key);
      }
    }
    
    // If still too many entries, keep only the most recent ones
    if (cacheRef.current.size > maxEntries) {
      const entries = Array.from(cacheRef.current.entries());
      cacheRef.current.clear();
      entries
        .sort((a, b) => b[1].t - a[1].t) // Sort by timestamp, newest first
        .slice(0, maxEntries)
        .forEach(([key, value]) => cacheRef.current.set(key, value));
    }
    // Persist latest state
    persistCache();
  };

  // Helpers to reduce duplication for two inputs
  const getField = (field) => field === 1 ? {
    name: name1,
    setName: setName1,
    selectedId: selectedId1,
    setSelectedId: setSelectedId1,
    suggestions: suggestions1,
    setSuggestions: setSuggestions1,
    showSuggestions: showSuggestions1,
    setShowSuggestions: setShowSuggestions1,
    isSearching: isSearching1,
    setIsSearching: setIsSearching1,
    showSearchButton: showSearchButton1,
    setShowSearchButton: setShowSearchButton1,
    inputRef: input1Ref,
    hideTimeoutRef: hideTimeout1Ref,
    abortController: abortController1,
  } : {
    name: name2,
    setName: setName2,
    selectedId: selectedId2,
    setSelectedId: setSelectedId2,
    suggestions: suggestions2,
    setSuggestions: setSuggestions2,
    showSuggestions: showSuggestions2,
    setShowSuggestions: setShowSuggestions2,
    isSearching: isSearching2,
    setIsSearching: setIsSearching2,
    showSearchButton: showSearchButton2,
    setShowSearchButton: setShowSearchButton2,
    inputRef: input2Ref,
    hideTimeoutRef: hideTimeout2Ref,
    abortController: abortController2,
  };

  const handleInput = (e, field) => {
    const f = getField(field);
    const v = e.target.value;
    f.setName(v);
    f.setSelectedId("");
    f.setShowSuggestions(false);
    f.setShowSearchButton(true);
  };

  const handleSearch = async (field) => {
    const f = getField(field);
    const value = f.name;
    if (value.trim().length < 2) return;
    f.setShowSearchButton(false);
    if (f.abortController.current) f.abortController.current.abort();
    f.abortController.current = new AbortController();
    await searchCelebrities(value, f.setSuggestions, f.abortController.current, f.setIsSearching);
    if (f.hideTimeoutRef.current) { clearTimeout(f.hideTimeoutRef.current); f.hideTimeoutRef.current = null; }
    f.setShowSuggestions(true);
  };

  const handleSuggestionClick = (field, item) => {
    const f = getField(field);
    f.setSelectedId(item.nconst);
    f.setName(item.name);
    f.setSuggestions([]);
    f.setShowSuggestions(false);
    f.setShowSearchButton(false);
    if (f.inputRef.current) f.inputRef.current.blur();
  };

  // Search celebrities in the graph for suggestions
  const searchCelebrities = async (q, setSuggestions, abortController, setIsSearching) => {
    if (!q || q.trim().length < 2) { setSuggestions([]); return; }
    
    setIsSearching(true);
    try {
      // Cleanup cache before checking
      cleanupCache();
      
      // cache hit
      const hit = cacheRef.current.get(q);
      if (hit && (Date.now() - hit.t) < 60000) {
        setSuggestions(hit.data);
        return;
      }
      // Use graph-based search for exact nconst matches
      const res = await fetch(`/api/search-celebrities-graph?q=${encodeURIComponent(q)}`, {
        signal: abortController.signal
      });
      const items = await res.json();
      const suggestions = Array.isArray(items) ? items.slice(0, 20).map(x => ({ nconst: x.nconst, name: x.name })) : [];
      setSuggestions(suggestions);
      cacheRef.current.set(q, { t: Date.now(), data: suggestions });
      // Persist for both inputs to use after reloads
      persistCache();
    } catch {
      setSuggestions([]);
    } finally {
      setIsSearching(false);
    }
  };

  // Removed per-field duplicates in favor of generic handlers above

  // Handle immediate search when Enter is pressed
  const handleKeyPress = async (e, field) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      await handleSearch(field);
    }
  };

  // Periodic cache cleanup to prevent memory leaks
  useEffect(() => {
    const interval = setInterval(cleanupCache, 30000); // Clean every 30 seconds
    return () => clearInterval(interval);
  }, []);

  const isSelected1 = !!selectedId1;
  const isSelected2 = !!selectedId2;
  const canSubmit = isSelected1 && isSelected2;

  const renderInput = (field, placeholder) => {
    const f = getField(field);
    const isSelected = !!f.selectedId;
    return (
      <div className="relative w-full">
        <input
          ref={f.inputRef}
          className={`w-full p-3 pr-10 rounded bg-gray-800 text-white placeholder-gray-400 border transition-colors duration-200 focus:outline-none ${isSelected ? 'border-green-500 focus:ring-2 focus:ring-green-500' : 'border-gray-700 focus:ring-2 focus:ring-blue-500'}`}
          placeholder={placeholder}
          value={f.name}
          onChange={(e) => handleInput(e, field)}
          onKeyPress={(e) => handleKeyPress(e, field)}
          onBlur={() => {
            if (f.hideTimeoutRef.current) clearTimeout(f.hideTimeoutRef.current);
            f.hideTimeoutRef.current = setTimeout(() => {
              f.setShowSuggestions(false);
              f.hideTimeoutRef.current = null;
            }, 150);
          }}
        />
        {isSelected ? (
          <span className="absolute right-3 top-1/2 -translate-y-1/2 text-green-400">
            <FaCheck />
          </span>
        ) : f.isSearching ? (
          <span className="absolute right-3 top-1/2 -translate-y-1/2 text-blue-400">
            <FaSpinner className="animate-spin" />
          </span>
        ) : f.showSearchButton ? (
          <button
            type="button"
            onClick={() => handleSearch(field)}
            disabled={f.name.trim().length < 2}
            className="absolute right-2 top-1/2 -translate-y-1/2 w-8 h-8 flex items-center justify-center text-gray-400 hover:text-blue-400 disabled:text-gray-600 disabled:cursor-not-allowed transition-colors rounded-md hover:bg-gray-700"
          >
            <FaSearch className="text-lg" />
          </button>
        ) : null}
        {f.showSuggestions && f.suggestions.length > 0 && (
          <ul className="absolute z-10 w-full bg-gray-900 border border-gray-700 rounded mt-1 max-h-60 overflow-y-auto">
            {f.suggestions.map((item, idx) => (
              <SuggestionItem
                key={idx}
                item={item}
                onClick={() => handleSuggestionClick(field, item)}
              />
            ))}
          </ul>
        )}
      </div>
    );
  };

  return (
    <form
      onSubmit={async e => {
        e.preventDefault();
        // Enforce both selections before submitting
        if (!canSubmit) return;
        onSearch(selectedId1, selectedId2);
      }}
      className="flex flex-col gap-4 items-center w-full max-w-lg"
      autoComplete="off"
    >
      {renderInput(1, "Enter first celebrity name...")}
      {renderInput(2, "Enter second celebrity name...")}
      <button
        type="submit"
        disabled={!canSubmit}
        aria-disabled={!canSubmit}
        className={`w-full font-bold py-3 px-6 rounded transition-colors ${canSubmit ? 'bg-blue-800 hover:bg-blue-900 text-white' : 'bg-gray-700 text-gray-300 cursor-not-allowed'}`}
      >
        Find Shortest Path
      </button>
    </form>
  );
}

// Suggestion item component with photo
function SuggestionItem({ item, onClick }) {
  const [photoUrl, setPhotoUrl] = useState(null);
  const [photoLoading, setPhotoLoading] = useState(true);

  useEffect(() => {
    const fetchPhoto = async () => {
      try {
        const response = await fetch(`/api/celebrity-photo?celebrityId=${encodeURIComponent(item.nconst)}&celebrityName=${encodeURIComponent(item.name || '')}`);
        const data = await response.json();
        if (data.photoUrl) {
          setPhotoUrl(data.photoUrl);
        }
      } catch (error) {
        console.log('Error fetching photo:', error);
      } finally {
        setPhotoLoading(false);
      }
    };

    fetchPhoto();
  }, [item.nconst, item.name]);

  return (
    <li
      className="px-4 py-2 hover:bg-blue-700 cursor-pointer text-white flex items-center gap-3"
      onMouseDown={onClick}
    >
      <div className="w-8 h-8 rounded-lg bg-blue-800 flex items-center justify-center text-xs overflow-hidden">
        {photoLoading ? (
          <div className="w-full h-full bg-gray-600 animate-pulse" />
        ) : photoUrl ? (
          <Image
            src={photoUrl}
            alt={item.name}
            width={32}
            height={32}
            className="w-full h-full object-cover rounded-lg"
            onError={() => setPhotoUrl(null)}
          />
        ) : (
          item.name.charAt(0)
        )}
      </div>
      <span>{item.name}</span>
    </li>
  );
} 