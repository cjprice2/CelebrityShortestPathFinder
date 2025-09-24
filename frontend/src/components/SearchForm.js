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
  const searchTimeout1 = useRef(null);
  const searchTimeout2 = useRef(null);
  const abortController1 = useRef(null);
  const abortController2 = useRef(null);
  // simple in-memory cache for suggestions (TTL 60s, max 100 entries)
  const cacheRef = useRef(new Map());
  // cache for celebrity photos
  const photoCacheRef = useRef(new Map());

  // Get celebrity photo URL
  const getCelebrityPhoto = async (celebrityId, celebrityName) => {
    if (!celebrityId) return null;
    
    // Check photo cache first
    const cachedPhoto = photoCacheRef.current.get(celebrityId);
    if (cachedPhoto) return cachedPhoto;
    
    try {
      const response = await fetch(`/api/celebrity-photo?celebrityId=${encodeURIComponent(celebrityId)}&celebrityName=${encodeURIComponent(celebrityName || '')}`);
      const data = await response.json();
      const photoUrl = data.photoUrl;
      
      if (photoUrl) {
        photoCacheRef.current.set(celebrityId, photoUrl);
        return photoUrl;
      }
    } catch (error) {
      console.log('Error fetching photo:', error);
    }
    
    return null;
  };

  // Cleanup cache to prevent memory leaks
  const cleanupCache = () => {
    const now = Date.now();
    const maxEntries = 100;
    
    // Remove expired entries
    for (const [key, value] of cacheRef.current.entries()) {
      if (now - value.t > 60000) {
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
    } catch {
      setSuggestions([]);
    } finally {
      setIsSearching(false);
    }
  };

  const handleInput1 = (e) => { 
    const v = e.target.value; 
    setName1(v); 
    setSelectedId1(""); 
    setShowSuggestions1(false);
    setShowSearchButton1(true);
  };

  const handleInput2 = (e) => { 
    const v = e.target.value; 
    setName2(v); 
    setSelectedId2(""); 
    setShowSuggestions2(false);
    setShowSearchButton2(true);
  };

  const handleSearch1 = async () => {
    if (name1.trim().length < 2) return;
    setShowSearchButton1(false);
    if (abortController1.current) abortController1.current.abort();
    abortController1.current = new AbortController();
    await searchCelebrities(name1, setSuggestions1, abortController1.current, setIsSearching1);
    setShowSuggestions1(true);
  };

  const handleSearch2 = async () => {
    if (name2.trim().length < 2) return;
    setShowSearchButton2(false);
    if (abortController2.current) abortController2.current.abort();
    abortController2.current = new AbortController();
    await searchCelebrities(name2, setSuggestions2, abortController2.current, setIsSearching2);
    setShowSuggestions2(true);
  };

  const handleSuggestionClick1 = (item) => {
    setSelectedId1(item.nconst);
    setName1(item.name);
    setSuggestions1([]);
    setShowSuggestions1(false);
    setShowSearchButton1(false);
    if (input1Ref.current) input1Ref.current.blur();
  };

  const handleSuggestionClick2 = (item) => {
    setSelectedId2(item.nconst);
    setName2(item.name);
    setSuggestions2([]);
    setShowSuggestions2(false);
    setShowSearchButton2(false);
    if (input2Ref.current) input2Ref.current.blur();
  };

  // Handle immediate search when Enter is pressed
  const handleKeyPress = async (e, field) => {
    if (e.key === 'Enter') {
      e.preventDefault();
      if (field === 1) {
        await handleSearch1();
      } else {
        await handleSearch2();
      }
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
      <div className="relative w-full">
        <input
          ref={input1Ref}
          className={`w-full p-3 pr-10 rounded bg-gray-800 text-white placeholder-gray-400 border transition-colors duration-200 focus:outline-none ${isSelected1 ? 'border-green-500 focus:ring-2 focus:ring-green-500' : 'border-gray-700 focus:ring-2 focus:ring-blue-500'}`}
          placeholder="Enter first celebrity name..."
          value={name1}
          onChange={handleInput1}
          onKeyPress={(e) => handleKeyPress(e, 1)}
          onBlur={() => setTimeout(() => setShowSuggestions1(false), 100)}
        />
        {isSelected1 ? (
          <span className="absolute right-3 top-1/2 -translate-y-1/2 text-green-400">
            <FaCheck />
          </span>
        ) : isSearching1 ? (
          <span className="absolute right-3 top-1/2 -translate-y-1/2 text-blue-400">
            <FaSpinner className="animate-spin" />
          </span>
        ) : showSearchButton1 ? (
          <button
            type="button"
            onClick={handleSearch1}
            disabled={name1.trim().length < 2}
            className="absolute right-2 top-1/2 -translate-y-1/2 w-8 h-8 flex items-center justify-center text-gray-400 hover:text-blue-400 disabled:text-gray-600 disabled:cursor-not-allowed transition-colors rounded-md hover:bg-gray-700"
          >
            <FaSearch className="text-lg" />
          </button>
        ) : null}
        {showSuggestions1 && suggestions1.length > 0 && (
          <ul className="absolute z-10 w-full bg-gray-900 border border-gray-700 rounded mt-1 max-h-60 overflow-y-auto">
            {suggestions1.map((item, idx) => (
              <SuggestionItem
                key={idx}
                item={item}
                onClick={() => handleSuggestionClick1(item)}
              />
            ))}
          </ul>
        )}
      </div>
      <div className="relative w-full">
        <input
          ref={input2Ref}
          className={`w-full p-3 pr-10 rounded bg-gray-800 text-white placeholder-gray-400 border transition-colors duration-200 focus:outline-none ${isSelected2 ? 'border-green-500 focus:ring-2 focus:ring-green-500' : 'border-gray-700 focus:ring-2 focus:ring-blue-500'}`}
          placeholder="Enter second celebrity name..."
          value={name2}
          onChange={handleInput2}
          onKeyPress={(e) => handleKeyPress(e, 2)}
          onBlur={() => setTimeout(() => setShowSuggestions2(false), 100)}
        />
        {isSelected2 ? (
          <span className="absolute right-3 top-1/2 -translate-y-1/2 text-green-400">
            <FaCheck />
          </span>
        ) : isSearching2 ? (
          <span className="absolute right-3 top-1/2 -translate-y-1/2 text-blue-400">
            <FaSpinner className="animate-spin" />
          </span>
        ) : showSearchButton2 ? (
          <button
            type="button"
            onClick={handleSearch2}
            disabled={name2.trim().length < 2}
            className="absolute right-2 top-1/2 -translate-y-1/2 w-8 h-8 flex items-center justify-center text-gray-400 hover:text-blue-400 disabled:text-gray-600 disabled:cursor-not-allowed transition-colors rounded-md hover:bg-gray-700"
          >
            <FaSearch className="text-lg" />
          </button>
        ) : null}
        {showSuggestions2 && suggestions2.length > 0 && (
          <ul className="absolute z-10 w-full bg-gray-900 border border-gray-700 rounded mt-1 max-h-60 overflow-y-auto">
            {suggestions2.map((item, idx) => (
              <SuggestionItem
                key={idx}
                item={item}
                onClick={() => handleSuggestionClick2(item)}
              />
            ))}
          </ul>
        )}
      </div>
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