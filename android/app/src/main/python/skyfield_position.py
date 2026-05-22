# Skyfield position calculation module for OnStepX APP
# Uses JPL DE421 ephemeris for high-precision planetary positions
#
# The de421.bsp file is bundled in Android assets at:
#   app/src/main/assets/de421.bsp
# SkyGotoApplication extracts it to internal storage:
#   /data/data/com.skygoto.app/files/de421.bsp

import os
from datetime import datetime, timezone

# Chaquopy's built-in CA bundle (same as certifi's cacert.pem but avoids import certifi)
# This is used by skyfield's iokit.py for HTTPS downloads of IERS data
_CHAQUOPY_CACERT = '/data/data/com.skygoto.app/files/chaquopy/cacert.pem'
if os.path.exists(_CHAQUOPY_CACERT):
    os.environ['SSL_CERT_FILE'] = _CHAQUOPY_CACERT
    os.environ['SSL_CERT_DIR'] = '/data/data/com.skygoto.app/files/chaquopy'

# Debug log file - use SAME file as AppLogger
LOG_FILE = '/data/data/com.skygoto.app/files/logs/onstepx_app.log'

def _log(msg):
    """Write debug message to SAME log file as AppLogger"""
    try:
        log_dir = os.path.dirname(LOG_FILE)
        if not os.path.exists(log_dir):
            os.makedirs(log_dir, exist_ok=True)
        with open(LOG_FILE, 'a') as f:
            timestamp = datetime.now().isoformat()
            f.write(f"[{timestamp}] [skyfield] {msg}\n")
    except:
        pass  # Ignore logging errors

def calculate_planet_position(planet_name, latitude, longitude, jd_utc):
    """
    Calculate RA/Dec using JPL DE421 ephemeris with geocentric observation.
    
    Args:
        planet_name: Name of the planet ('sun', 'moon', 'mercury', etc.)
        latitude: Observer latitude in degrees (unused for geocentric)
        longitude: Observer longitude in degrees (unused for geocentric)
        jd_utc: Julian Date in UTC
    
    Returns:
        Tuple of (ra_string, dec_string) in LX200 format (e.g., "03:49:55", "+20*04:50")
    """
    try:
        from skyfield.api import load
        
        # Load ephemeris
        bsp_path = '/data/data/com.skygoto.app/files/de421.bsp'
        if not os.path.exists(bsp_path):
            _log(f"ERROR: de421.bsp not found at {bsp_path}")
            raise FileNotFoundError(f"de421.bsp not found")
        
        planets = load(bsp_path)
        ts = load.timescale()
        
        # Convert JD to Skyfield Time
        unix_time = (jd_utc - 2440587.5) * 86400.0
        dt = datetime.fromtimestamp(unix_time, tz=timezone.utc)
        t = ts.from_datetime(dt)
        
        # Planet name mapping
        planet_map = {
            'sun': 'Sun',
            'moon': 'Moon',
            'mercury': 'Mercury Barycenter',
            'venus': 'Venus Barycenter',
            'mars': 'Mars Barycenter',
            'jupiter': 'Jupiter Barycenter',
            'saturn': 'Saturn Barycenter',
            'uranus': 'Uranus Barycenter',
            'neptune': 'Neptune Barycenter',
            'pluto': 'Pluto Barycenter',
        }
        
        name_lower = planet_name.lower()
        if name_lower not in planet_map:
            raise ValueError(f"Unknown planet: {planet_name}")
        
        body = planets[planet_map[name_lower]]
        earth = planets['Earth']
        
        # Calculate apparent position (geocentric)
        ra, dec, distance = earth.at(t).observe(body).apparent().radec('date')
        
        # Format RA as HH:MM:SS
        ra_hms = ra.hms()
        ra_str = f"{int(ra_hms[0]):02d}:{int(ra_hms[1]):02d}:{int(ra_hms[2] * 100) // 100:02d}"
        
        # Format Dec as sDD*MM:SS
        dec_dms = dec.signed_dms()
        sign = '+' if dec_dms[0] >= 0 else '-'
        dec_str = f"{sign}{int(abs(dec_dms[1])):02d}*{int(dec_dms[2]):02d}:{int(dec_dms[3] * 100) // 100:02d}"
        
        _log(f"SUCCESS {planet_name}: RA={ra_str}, Dec={dec_str}, dist={distance.au:.4f} au")
        return ra_str, dec_str
        
    except Exception as e:
        _log(f"ERROR {planet_name}: {type(e).__name__}: {e}")
        import traceback
        _log(f"Traceback: {traceback.format_exc()}")
        raise


def get_version():
    """Return Skyfield version for debugging."""
    import skyfield
    return skyfield.__version__