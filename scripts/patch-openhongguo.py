#!/usr/bin/env python3
"""Apply Kaixinguo's Android diagnostics and signed video_tab playback recovery to pinned openhongguo v0.4.0.

The upstream repository remains untouched. CI checks out the immutable tag,
applies this audited patch, runs Go/Kotlin tests, and builds the AAR embedded
in the Android release APKs.
"""
from pathlib import Path
import subprocess
import sys

ROOT = Path(sys.argv[1]).resolve()

def replace_once(path: str, old: str, new: str) -> None:
    file = ROOT / path
    source = file.read_text(encoding="utf-8")
    count = source.count(old)
    if count != 1:
        raise SystemExit(f"{path}: expected exactly one patch target, found {count}")
    file.write_text(source.replace(old, new, 1), encoding="utf-8")

replace_once(
    "sdk/errors.go",
    """type APIError struct {
	// HTTPStatus 是响应状态码。
	HTTPStatus int
	// Code 是失败所带的服务端错误码；若只是普通 HTTP 失败，客户端将其留空。
	Code    string
	Message string
}""",
    """type APIError struct {
	// HTTPStatus 是响应状态码。
	HTTPStatus int
	// Code 是服务器响应中的业务码原文，保留字符串形式。
	Code string
	Name string
	Message string
	Method string
	Path string
	ResponseBytes int
	DecodedResponseBytes int
	ContentType string
	ContentEncoding string
}""",
)

replace_once(
    "sdk/errors.go",
    """func (e *APIError) Error() string {
	if e == nil {
		return "openhongguo: API error"
	}
	return fmt.Sprintf("openhongguo: API error (HTTP %d, code %s)", e.HTTPStatus, e.Code)
}""",
    """func (e *APIError) Error() string {
	if e == nil {
		return "openhongguo: API error"
	}
	// Preserve the CLI's established user-facing wording. Structured server
	// details are carried separately through the Android binding.
	switch e.HTTPStatus {
	case 401, 403:
		return "openhongguo: authentication required"
	case 429:
		return "openhongguo: rate limited"
	}
	if e.Message != "" {
		return fmt.Sprintf("openhongguo: %s (HTTP %d, code %s)", e.Message, e.HTTPStatus, e.Code)
	}
	return fmt.Sprintf("openhongguo: API error (HTTP %d, code %s)", e.HTTPStatus, e.Code)
}""",
)

replace_once(
    "sdk/errors.go",
    """type CodeError struct {
	// Code 是响应体中的业务码，服务端只给出消息时为 0；
	// Name 是已知的业务码符号名。
	Code    int
	Name    string
	Message string
	// Kind 是该业务码对返回它的接口而言的含义。
	Kind protocol.ErrorKind
	// HTTPStatus 和 Path 描述失败的请求；客户端目前尚未填充它们。
	HTTPStatus int
	Path       string
}""",
    """type CodeError struct {
	// Code 是便于分类的数值业务码；RawCode 保留服务器返回的原始字符串。
	Code int
	RawCode string
	// Name 是已知的业务码符号名。
	Name string
	Message string
	// Kind 是该业务码对返回它的接口而言的含义。
	Kind protocol.ErrorKind
	HTTPStatus int
	Method string
	Path string
	ResponseBytes int
	DecodedResponseBytes int
	ContentType string
	ContentEncoding string
}""",
)

replace_once(
    "sdk/errors.go",
    """	name := e.Name
	if name == "" {
		name = e.Kind.String()
	}
	return fmt.Sprintf("openhongguo: %s (code %d, %s)", e.Message, e.Code, name)
}""",
    """	name := e.Name
	if name == "" {
		name = e.Kind.String()
	}
	code := e.RawCode
	if code == "" {
		code = fmt.Sprint(e.Code)
	}
	return fmt.Sprintf("openhongguo: %s (code %s, %s)", e.Message, code, name)
}""",
)

replace_once(
    "sdk/errors.go",
    "// CodeError 表示一次携带业务码的服务端失败。",
    """// AnonymousResponseError 表示服务端返回了成功 HTTP 状态，但响应体为空。
// Is keeps the existing ErrAnonymousLimited matching and automatic device recovery.
type AnonymousResponseError struct {
	HTTPStatus int
	Method string
	Path string
	ResponseBytes int
	DecodedResponseBytes int
	ContentType string
	ContentEncoding string
	RetryAfter string
}

func (e *AnonymousResponseError) Error() string {
	return "openhongguo: anonymous response was empty"
}

func (e *AnonymousResponseError) Is(target error) bool {
	return target == ErrAnonymousLimited
}

// CodeError 表示一次携带业务码的服务端失败。""",
)

replace_once(
    "sdk/client.go",
    "raw, err := readAnonymous(resp)",
    "raw, err := readAnonymous(resp, method, req.URL)",
)

replace_once(
    "sdk/client.go",
    """func statusOf(resp *Response) int {
	if resp == nil {
		return 0
	}
	return resp.StatusCode
}

func readAnonymous(resp *Response) ([]byte, error) {
	if resp == nil {
		return nil, fmt.Errorf("%w: nil response", ErrTransport)
	}
	switch resp.StatusCode {
	case 401, 403:
		return nil, ErrAuthenticationRequired
	case 429:
		return nil, ErrRateLimited
	}
	if resp.StatusCode < 200 || resp.StatusCode >= 300 {
		return nil, &APIError{HTTPStatus: resp.StatusCode, Message: "HTTP request failed"}
	}
	body, err := decodeBody(resp)
	if err != nil {
		return nil, err
	}
	if len(bytes.TrimSpace(body)) == 0 {
		return nil, ErrAnonymousLimited
	}
	if code, message, ok := protocol.BusinessCode(body); ok && code != "0" {
		n, convErr := strconv.Atoi(code)
		if convErr != nil {
			if protocol.AuthCode(code, message) {
				return nil, ErrAuthenticationRequired
			}
			return nil, &CodeError{Message: message, Kind: protocol.Classify(0, message)}
		}
		kind := protocol.Classify(n, message)
		return nil, &CodeError{
			Code: n, Name: protocol.CodeName(n), Message: message, Kind: kind,
		}
	}
	if status := protocol.BaseStatus(body); protocol.DeviceRejected(status) {
		return nil, &CodeError{Code: status, Name: "DEVICE_REJECTED", Kind: protocol.KindDeviceRejected}
	}
	return body, nil
}""",
    """func statusOf(resp *Response) int {
	if resp == nil {
		return 0
	}
	return resp.StatusCode
}

func responseHeader(resp *Response, name string) string {
	if resp == nil {
		return ""
	}
	for key, value := range resp.Headers {
		if strings.EqualFold(key, name) {
			return value
		}
	}
	return ""
}

// requestPath keeps only host and path. Query parameters contain anonymous
// device identifiers, tickets and signed playback material, so never expose them.
func requestPath(rawURL string) string {
	u, err := url.Parse(rawURL)
	if err != nil || u.Host == "" {
		return ""
	}
	path := u.EscapedPath()
	if path == "" {
		path = "/"
	}
	return u.Host + path
}

func readAnonymous(resp *Response, method, rawURL string) ([]byte, error) {
	if resp == nil {
		return nil, fmt.Errorf("%w: nil response", ErrTransport)
	}
	body, err := decodeBody(resp)
	if err != nil {
		return nil, err
	}

	status := resp.StatusCode
	rawCode, serverMessage, hasBusinessCode := protocol.BusinessCode(body)
	serverName := ""
	if n, convErr := strconv.Atoi(rawCode); convErr == nil {
		serverName = protocol.CodeName(n)
	}
	meta := struct {
		method string
		path string
		responseBytes int
		decodedResponseBytes int
		contentType string
		contentEncoding string
	}{
		method: method,
		path: requestPath(rawURL),
		responseBytes: len(resp.Body),
		decodedResponseBytes: len(body),
		contentType: responseHeader(resp, "Content-Type"),
		contentEncoding: responseHeader(resp, "Content-Encoding"),
	}

	// Keep the HTTP layer distinct from the API business code. APIError.Is
	// still maps 401/403/429 to the existing SDK sentinel errors.
	if status < 200 || status >= 300 {
		return nil, &APIError{
			HTTPStatus: status, Code: rawCode, Name: serverName, Message: serverMessage,
			Method: meta.method, Path: meta.path, ResponseBytes: meta.responseBytes,
			DecodedResponseBytes: meta.decodedResponseBytes, ContentType: meta.contentType,
			ContentEncoding: meta.contentEncoding,
		}
	}
	if len(bytes.TrimSpace(body)) == 0 {
		return nil, &AnonymousResponseError{
			HTTPStatus: status, Method: meta.method, Path: meta.path,
			ResponseBytes: meta.responseBytes, DecodedResponseBytes: meta.decodedResponseBytes,
			ContentType: meta.contentType, ContentEncoding: meta.contentEncoding,
			RetryAfter: responseHeader(resp, "Retry-After"),
		}
	}
	if hasBusinessCode && rawCode != "0" {
		n, convErr := strconv.Atoi(rawCode)
		kind := protocol.Classify(n, serverMessage)
		if protocol.AuthCode(rawCode, serverMessage) {
			kind = protocol.KindAuth
		}
		if convErr != nil {
			n = 0
		}
		return nil, &CodeError{
			Code: n, RawCode: rawCode, Name: serverName, Message: serverMessage, Kind: kind,
			HTTPStatus: status, Method: meta.method, Path: meta.path,
			ResponseBytes: meta.responseBytes, DecodedResponseBytes: meta.decodedResponseBytes,
			ContentType: meta.contentType, ContentEncoding: meta.contentEncoding,
		}
	}
	if baseStatus := protocol.BaseStatus(body); protocol.DeviceRejected(baseStatus) {
		return nil, &CodeError{
			Code: baseStatus, RawCode: strconv.Itoa(baseStatus), Name: "DEVICE_REJECTED",
			Kind: protocol.KindDeviceRejected, HTTPStatus: status, Method: meta.method,
			Path: meta.path, ResponseBytes: meta.responseBytes,
			DecodedResponseBytes: meta.decodedResponseBytes, ContentType: meta.contentType,
			ContentEncoding: meta.contentEncoding,
		}
	}
	return body, nil
}""",
)

replace_once(
    "bindings/internal/dispatch/dispatch.go",
    """	return map[string]any{
		"code":         code,
		"message":      err.Error(),
		"retryable":    sdk.Retryable(err),
		"retryAfterMs": sdk.RetryAfter(err, 0).Milliseconds(),
	}
}""",
    """	out := map[string]any{
		"code":         code,
		"message":      err.Error(),
		"retryable":    sdk.Retryable(err),
		"retryAfterMs": sdk.RetryAfter(err, 0).Milliseconds(),
	}
	var service *sdk.CodeError
	if errors.As(err, &service) {
		serverCode := service.RawCode
		if serverCode == "" && service.Code != 0 {
			serverCode = fmt.Sprint(service.Code)
		}
		out["serverCode"] = serverCode
		out["serverName"] = service.Name
		out["serverMessage"] = service.Message
		out["httpStatus"] = service.HTTPStatus
		out["requestMethod"] = service.Method
		out["requestPath"] = service.Path
		out["responseBytes"] = service.ResponseBytes
		out["decodedResponseBytes"] = service.DecodedResponseBytes
		out["responseContentType"] = service.ContentType
		out["responseContentEncoding"] = service.ContentEncoding
		return out
	}
	var api *sdk.APIError
	if errors.As(err, &api) {
		out["serverCode"] = api.Code
		out["serverName"] = api.Name
		out["serverMessage"] = api.Message
		out["httpStatus"] = api.HTTPStatus
		out["requestMethod"] = api.Method
		out["requestPath"] = api.Path
		out["responseBytes"] = api.ResponseBytes
		out["decodedResponseBytes"] = api.DecodedResponseBytes
		out["responseContentType"] = api.ContentType
		out["responseContentEncoding"] = api.ContentEncoding
		return out
	}
	var empty *sdk.AnonymousResponseError
	if errors.As(err, &empty) {
		out["httpStatus"] = empty.HTTPStatus
		out["requestMethod"] = empty.Method
		out["requestPath"] = empty.Path
		out["responseBytes"] = empty.ResponseBytes
		out["decodedResponseBytes"] = empty.DecodedResponseBytes
		out["responseContentType"] = empty.ContentType
		out["responseContentEncoding"] = empty.ContentEncoding
	}
	return out
}""",
)

replace_once(
    "bindings/mobile/android/src/main/kotlin/dev/openhongguo/Errors.kt",
    """    /** 服务端建议的等待时间，单位毫秒；0 表示没有建议。 */
    val retryAfterMs: Long = 0,
) : Exception(message) {""",
    """    /** 服务端建议的等待时间，单位毫秒；0 表示没有建议。 */
    val retryAfterMs: Long = 0,
    /** 服务端原始业务码，字符串形式保留前导零。 */
    val serverCode: String? = null,
    /** 服务端业务码名称（SDK 能识别时提供）。 */
    val serverName: String? = null,
    /** 服务端原始 message 字段。 */
    val serverMessage: String? = null,
    /** HTTP 状态码，与服务端业务码分开。 */
    val httpStatus: Int? = null,
    /** 请求方法和去掉 query 的 host/path。 */
    val requestMethod: String? = null,
    val requestPath: String? = null,
    /** 实际收到的响应字节数，以及解压后的应用层字节数。 */
    val responseBytes: Int? = null,
    val decodedResponseBytes: Int? = null,
    val responseContentType: String? = null,
    val responseContentEncoding: String? = null,
) : Exception(message) {""",
)

replace_once(
    "bindings/mobile/android/src/main/kotlin/dev/openhongguo/Errors.kt",
    """            retryable = o.optBoolean("retryable"),
            retryAfterMs = o.optLong("retryAfterMs"),
        )""",
    """            retryable = o.optBoolean("retryable"),
            retryAfterMs = o.optLong("retryAfterMs"),
            serverCode = o.nullableString("serverCode"),
            serverName = o.nullableString("serverName"),
            serverMessage = o.nullableString("serverMessage"),
            httpStatus = o.nullableInt("httpStatus"),
            requestMethod = o.nullableString("requestMethod"),
            requestPath = o.nullableString("requestPath"),
            responseBytes = o.nullableInt("responseBytes"),
            decodedResponseBytes = o.nullableInt("decodedResponseBytes"),
            responseContentType = o.nullableString("responseContentType"),
            responseContentEncoding = o.nullableString("responseContentEncoding"),
        )""",
)

with (ROOT / "bindings/mobile/android/src/main/kotlin/dev/openhongguo/Errors.kt").open("a", encoding="utf-8") as f:
    f.write("""

private fun JSONObject.nullableString(name: String): String? =
    if (!has(name) || isNull(name)) null else optString(name).takeIf { it.isNotEmpty() }

private fun JSONObject.nullableInt(name: String): Int? =
    if (!has(name) || isNull(name)) null else optInt(name)
""")

replace_once(
    "sdk/errors_test.go",
    "func TestCodeErrorMapsToOneSentinel(t *testing.T) {",
    """func TestAnonymousResponseErrorKeepsLegacySentinel(t *testing.T) {
	err := &AnonymousResponseError{HTTPStatus: 200, ResponseBytes: 0, DecodedResponseBytes: 0}
	if !errors.Is(err, ErrAnonymousLimited) {
		t.Fatalf("empty response no longer matches ErrAnonymousLimited: %v", err)
	}
}

func TestCodeErrorPreservesLiteralServerCode(t *testing.T) {
	err := &CodeError{Code: 100103, RawCode: "00100103", Name: "PARAM_INVALID", Message: "invalid"}
	if got := err.Error(); !stringsContains(got, "code 00100103") {
		t.Fatalf("error lost the server's literal code: %s", got)
	}
}

func TestCodeErrorMapsToOneSentinel(t *testing.T) {""",
)

replace_once(
    "sdk/catalog_test.go",
    "func TestAnonymousReadsExposeIDsAndTitles(t *testing.T) {",
    """func TestSearchErrorPreservesServerBusinessDetails(t *testing.T) {
	body := []byte(`{"code":"00100103","message":"server rejected this request"}`)
	client, _ := testClient(t, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json; charset=utf-8")
		switch r.URL.Path {
		case protocol.PathRegister:
			_, _ = w.Write(fixture(t, "register-response.json"))
		case protocol.PathSearch:
			_, _ = w.Write(body)
		default:
			http.NotFound(w, r)
		}
	}))
	_, err := client.Search(context.Background(), "fixture", sdk.SearchOptions{})
	var service *sdk.CodeError
	if !errors.As(err, &service) {
		t.Fatalf("expected CodeError, got %T: %v", err, err)
	}
	if service.RawCode != "00100103" || service.Name != "PARAM_INVALID" {
		t.Fatalf("server business code was normalized or lost: %+v", service)
	}
	if service.Message != "server rejected this request" || service.HTTPStatus != http.StatusOK {
		t.Fatalf("server message/status lost: %+v", service)
	}
	if service.ResponseBytes != len(body) || service.DecodedResponseBytes != len(body) {
		t.Fatalf("response sizes = %d/%d, want %d", service.ResponseBytes, service.DecodedResponseBytes, len(body))
	}
	if !strings.HasSuffix(service.Path, protocol.PathSearch) || service.Method == "" || service.ContentType == "" {
		t.Fatalf("request metadata incomplete: %+v", service)
	}
}

func TestSearchEmptyResponseRetainsHTTPMetadata(t *testing.T) {
	client, _ := testClient(t, http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		switch r.URL.Path {
		case protocol.PathRegister:
			_, _ = w.Write(fixture(t, "register-response.json"))
		case protocol.PathSearch:
			w.WriteHeader(http.StatusOK)
		default:
			http.NotFound(w, r)
		}
	}))
	_, err := client.Search(context.Background(), "fixture", sdk.SearchOptions{})
	if !errors.Is(err, sdk.ErrAnonymousLimited) {
		t.Fatalf("empty response error = %v", err)
	}
	var empty *sdk.AnonymousResponseError
	if !errors.As(err, &empty) {
		t.Fatalf("expected AnonymousResponseError, got %T", err)
	}
	if empty.HTTPStatus != http.StatusOK || empty.ResponseBytes != 0 || empty.DecodedResponseBytes != 0 {
		t.Fatalf("empty response metadata = %+v", empty)
	}
	if !strings.HasSuffix(empty.Path, protocol.PathSearch) || empty.Method == "" {
		t.Fatalf("empty response request metadata incomplete: %+v", empty)
	}
}

func TestAnonymousReadsExposeIDsAndTitles(t *testing.T) {""",
)

replace_once(
    "bindings/internal/dispatch/dispatch_test.go",
    """	"github.com/beilunyang/openhongguo/bindings/internal/fixtures"
	"github.com/beilunyang/openhongguo/sdk""",
    """	"github.com/beilunyang/openhongguo/bindings/internal/fixtures"
	"github.com/beilunyang/openhongguo/internal/protocol"
	"github.com/beilunyang/openhongguo/sdk""",
)

replace_once(
    "bindings/internal/dispatch/dispatch_test.go",
    "func TestCallCancelled(t *testing.T) {",
    """func TestErrorCarriesStructuredServerDetails(t *testing.T) {
	service := &sdk.CodeError{
		Code: 100103, RawCode: "00100103", Name: "PARAM_INVALID",
		Message: "original server message", Kind: protocol.KindParam,
		HTTPStatus: 200, Method: "POST", Path: "api.example/novel/player/video_model/v1/",
		ResponseBytes: 87, DecodedResponseBytes: 87,
		ContentType: "application/json", ContentEncoding: "gzip",
	}
	got := dispatch.Error(service)
	for key, want := range map[string]any{
		"serverCode": "00100103",
		"serverName": "PARAM_INVALID",
		"serverMessage": "original server message",
		"httpStatus": 200,
		"requestMethod": "POST",
		"requestPath": "api.example/novel/player/video_model/v1/",
		"responseBytes": 87,
		"decodedResponseBytes": 87,
		"responseContentType": "application/json",
		"responseContentEncoding": "gzip",
	} {
		if got[key] != want {
			t.Errorf("%s = %#v, want %#v", key, got[key], want)
		}
	}

	empty := &sdk.AnonymousResponseError{
		HTTPStatus: 200, Method: "POST", Path: "api.example/novel/player/video_model/v1/",
		ResponseBytes: 0, DecodedResponseBytes: 0,
	}
	got = dispatch.Error(empty)
	if got["code"] != "ANONYMOUS_LIMITED" || got["httpStatus"] != 200 || got["responseBytes"] != 0 {
		t.Fatalf("empty response details = %#v", got)
	}
}

func TestCallCancelled(t *testing.T) {""",
)

replace_once(
    "bindings/mobile/android/src/test/kotlin/dev/openhongguo/ClientTest.kt",
    """    @Test
    fun unknownErrorCodeMapsToUnknown() {""",
    '''    @Test
    fun serverResponseDetailsSurviveTheGoBridge() = runBlocking {
        val engine = FakeEngine { _, _ ->
            """{"error":{"code":"PARAM_INVALID","message":"openhongguo: original server message (code 00100103, PARAM_INVALID)","retryable":false,"retryAfterMs":0,"serverCode":"00100103","serverName":"PARAM_INVALID","serverMessage":"original server message","httpStatus":200,"requestMethod":"POST","requestPath":"api.example/novel/player/video_model/v1/","responseBytes":87,"decodedResponseBytes":87,"responseContentType":"application/json","responseContentEncoding":"gzip"}}"""
        }
        try {
            OpenhongguoClient(engine).hotSearch()
            fail("no exception")
        } catch (e: OpenhongguoException) {
            assertEquals(ErrorCode.PARAM_INVALID, e.code)
            assertEquals("00100103", e.serverCode)
            assertEquals("PARAM_INVALID", e.serverName)
            assertEquals("original server message", e.serverMessage)
            assertEquals(200, e.httpStatus)
            assertEquals("POST", e.requestMethod)
            assertEquals("api.example/novel/player/video_model/v1/", e.requestPath)
            assertEquals(87, e.responseBytes)
            assertEquals(87, e.decodedResponseBytes)
            assertEquals("application/json", e.responseContentType)
            assertEquals("gzip", e.responseContentEncoding)
        }
    }

    @Test
    fun unknownErrorCodeMapsToUnknown() {''',
)


replace_once(
    "internal/protocol/playback_v5.go",
    """	PathPlaybackV5      = "/novel/player/multi_video_model/player/v1"
	PlaybackVersionCode = 74332
	PlaybackVersionName = "7.4.3.32"
""",
    """	PathPlaybackV5      = "/novel/player/multi_video_model/player/v1"
	PathPlaybackVideoTab = "/reading/bookapi/video_tab/multi_video_model/v1/"
	PlaybackVersionCode = 74332
	PlaybackVersionName = "7.4.3.32"
""",
)

replace_once(
    "internal/protocol/playback_v5.go",
    "// PlaybackV5Body 只压缩一次；调用方将返回的原始字节同时用于签名和发送。",
    """// PlaybackVideoTabPairs is the ordered query for the signed video_tab recovery route.
// It keeps the currently registered identity but uses the compatible 7.3.9.32
// query family and asks for exactly one episode. No alternate episode is selected.
func PlaybackVideoTabPairs(id SearchIdentity, episodeID string) [][2]string {
	d := id.Device.OrDefault()
	kind, pad := "phone", "0"
	if d.Pad {
		kind, pad = "pad", "1"
	}
	const versionCode = "73932"
	return [][2]string{
		{"ac", "wifi"},
		{"channel", d.Channel},
		{"aid", AID},
		{"app_name", AppName},
		{"version_code", versionCode},
		{"version_name", "7.3.9.32"},
		{"device_platform", DevicePlatform},
		{"os", "android"},
		{"ssmix", "a"},
		{"device_type", d.Model},
		{"device_brand", d.Brand},
		{"language", "zh"},
		{"os_api", strconv.Itoa(d.OSAPI)},
		{"os_version", d.OSVersion},
		{"manifest_version_code", versionCode},
		{"resolution", d.Resolution},
		{"dpi", strconv.Itoa(d.DPI)},
		{"update_version_code", versionCode},
		{"host_abi", "arm64-v8a"},
		{"dragon_device_type", kind},
		{"pv_player", versionCode},
		{"compliance_status", "0"},
		{"need_personal_recommend", "1"},
		{"player_so_load", "1"},
		{"is_android_pad_screen", pad},
		{"iid", id.InstallID},
		{"cdid", id.CDID},
		{"device_id", id.DeviceID},
		{"video_id", episodeID},
		{"video_id_type", "0"},
		{"device_level", "1"},
		{"need_all_video_definition", "1"},
		{"need_mp4_align", "0"},
		{"use_os_player", "0"},
		{"video_platform", "1024"},
		{"source", "0"},
		{"from_video_id", ""},
	}
}

// PlaybackVideoTabUserAgent is the regular 7.1.3.32 client UA used by video_tab.
// The request query is signed separately with the 7.3.9.32 version family.
func PlaybackVideoTabUserAgent(d model.Device) string {
	d = d.OrDefault()
	return fmt.Sprintf("%s/71332 (Linux; U; Android %s; zh_CN; %s; Build/%s; Cronet/TTNetVersion:04657795 2026-01-23 QuicVersion:c67e9834 2025-09-08)",
		Package, d.OSVersion, d.Model, d.Build)
}

// PlaybackV5Body 只压缩一次；调用方将返回的原始字节同时用于签名和发送.""",
)

replace_once(
    "internal/protocol/parse.go",
    """	entry := env.Data
	if group, ok := entry["1004"]; ok {""",
    """	entry := env.Data
	// The signed video_tab endpoint wraps per-episode data in video_model_data.
	// Preserve the same strict episodeID selection as the V5 and legacy routes.
	if group, ok := entry["video_model_data"]; ok {
		var grouped map[string]json.RawMessage
		if err := json.Unmarshal(group, &grouped); err != nil {
			return nil, fmt.Errorf("playback video_model_data is not an object: %w", err)
		}
		entry = grouped
	}
	if group, ok := entry["1004"]; ok {""",
)

replace_once(
    "sdk/reset.go",
    """func needsFreshDevice(err error, playback bool) bool {
	if errors.Is(err, ErrDeviceRejected) {
		return true
	}
	return playback && errors.Is(err, ErrAnonymousLimited)
}""",
    """func needsFreshDevice(err error, playback bool) bool {
	if errors.Is(err, ErrDeviceRejected) {
		return true
	}
	// Retry-After is an explicit server backoff instruction, not a reason to
	// register more anonymous devices and immediately repeat the same request.
	var empty *AnonymousResponseError
	if errors.As(err, &empty) && empty.RetryAfter != "" {
		return false
	}
	return playback && errors.Is(err, ErrAnonymousLimited)
}""",
)

replace_once(
    "sdk/media.go",
    """	"fmt"
	"time"
""",
    """	"fmt"
	"strings"
	"time"
""",
)

replace_once(
    "sdk/media.go",
    """		var raw []byte
		for attempt := 0; attempt < 2; attempt++ {""",
    """		var raw []byte
		videoTabTried := false
		for attempt := 0; attempt < 2; attempt++ {""",
)

replace_once(
    "sdk/media.go",
    """			if !errors.Is(err, ErrAnonymousLimited) || attempt == 1 {
				break
			}
			if err = sleepContext(ctx, 300*time.Millisecond); err != nil {
				return nil, err
			}""",
    """			if errors.Is(err, ErrAnonymousLimited) && anonymousResponseHasRetryAfter(err) {
				// Do not retry or rotate identity when the server explicitly asks us to back off.
				break
			}
			if errors.Is(err, ErrAnonymousLimited) && !videoTabTried {
				videoTabTried = true
				if c.logger != nil {
					c.logger.Printf("V5 playback response was empty; trying signed video_tab GET for episode %s", episodeID)
				}
				tabQuery, tabHeaders, signErr := c.playbackSigner.Sign(
					protocol.PlaybackVideoTabPairs(c.identity(p), episodeID),
					c.clock().UnixMilli(), nil, nil,
				)
				if signErr != nil {
					if c.logger != nil {
						c.logger.Printf("video_tab playback recovery signing failed: %v", signErr)
					}
				} else {
					tabHeaders["User-Agent"] = protocol.PlaybackVideoTabUserAgent(c.deviceOf(p))
					tabHeaders["Accept"] = "application/json; charset=utf-8"
					tabHeaders["Accept-Language"] = "zh-CN,zh;q=0.9"
					tabURL := playbackVideoTabOrigin(c.apiOrigin) + protocol.PathPlaybackVideoTab + "?" + tabQuery
					tabRaw, _, tabErr := c.doRaw(ctx, "GET", tabURL, tabHeaders, nil)
					if ctxErr := ctx.Err(); ctxErr != nil {
						return nil, ctxErr
					}
					if tabErr == nil {
						if _, parseErr := protocol.ParsePlayback(tabRaw, episodeID); parseErr == nil {
							raw, err = tabRaw, nil
							if c.logger != nil {
								c.logger.Printf("video_tab playback recovery succeeded for episode %s", episodeID)
							}
							break
						}
						if c.logger != nil {
							c.logger.Printf("video_tab playback recovery returned no usable model for episode %s", episodeID)
						}
					} else {
						if playbackVideoTabFatal(tabErr) {
							return nil, tabErr
						}
						if c.logger != nil {
							c.logger.Printf("video_tab playback recovery failed for episode %s: %v", episodeID, tabErr)
						}
					}
				}
			}
			if !errors.Is(err, ErrAnonymousLimited) || attempt == 1 {
				break
			}
			if err = sleepContext(ctx, 300*time.Millisecond); err != nil {
				return nil, err
			}""",
)

replace_once(
    "sdk/media.go",
    """		if err != nil {
			if !errors.Is(err, ErrAnonymousLimited) && !playbackRouteUnavailable(err) {""",
    """		if err != nil && anonymousResponseHasRetryAfter(err) {
			return nil, err
		}
		if err != nil {
			if !errors.Is(err, ErrAnonymousLimited) && !playbackRouteUnavailable(err) {""",
)

replace_once(
    "sdk/media.go",
    """func playbackRouteUnavailable(err error) bool {
	var api *APIError
	return errors.As(err, &api) && (api.HTTPStatus == 404 || api.HTTPStatus == 405)
}""",
    """func playbackRouteUnavailable(err error) bool {
	var api *APIError
	return errors.As(err, &api) && (api.HTTPStatus == 404 || api.HTTPStatus == 405)
}

// playbackVideoTabOrigin uses the b host for production video_tab requests, while
// preserving a custom/test origin so the recovery path can be verified offline.
func playbackVideoTabOrigin(origin string) string {
	origin = strings.TrimRight(origin, "/")
	const aHost = "api5-normal-sinfonlinea.fqnovel.com"
	const bHost = "api5-normal-sinfonlineb.fqnovel.com"
	if strings.Contains(origin, aHost) {
		return strings.Replace(origin, aHost, bHost, 1)
	}
	return origin
}

func anonymousResponseHasRetryAfter(err error) bool {
	var empty *AnonymousResponseError
	return errors.As(err, &empty) && strings.TrimSpace(empty.RetryAfter) != ""
}

// Certain explicit server failures should be returned instead of immediately
// trying older routes. Network faults and 404/405 remain eligible for fallback.
func playbackVideoTabFatal(err error) bool {
	if anonymousResponseHasRetryAfter(err) ||
		errors.Is(err, ErrRateLimited) ||
		errors.Is(err, ErrAuthenticationRequired) ||
		errors.Is(err, ErrDeviceRejected) {
		return true
	}
	var code *CodeError
	if errors.As(err, &code) {
		return true
	}
	var api *APIError
	return errors.As(err, &api) && api.HTTPStatus >= 400 && api.HTTPStatus < 500 &&
		api.HTTPStatus != 404 && api.HTTPStatus != 405
}""",
)

replace_once(
    "sdk/reset_test.go",
    "func TestEmptyPlaybackMovesToFreshDevice(t *testing.T) {",
    """func TestEmptyV5PlaybackRecoversViaSignedVideoTab(t *testing.T) {
	const episodeID = "7100000000000201"
	reg := fixture(t, "register-response.json")
	var registers, v5Calls, tabCalls, legacyCalls int
	var v5DeviceID, tabDeviceID, v5InstallID, tabInstallID, v5CDID, tabCDID string
	var badTabRequest string
	srv := httptest.NewServer(http.HandlerFunc(func(w http.ResponseWriter, r *http.Request) {
		w.Header().Set("Content-Type", "application/json")
		switch r.URL.Path {
		case protocol.PathRegister:
			registers++
			w.Header().Add("Set-Cookie", "ttreq=fixture-ticket; Path=/")
			_, _ = w.Write(reg)
		case protocol.PathPlaybackV5:
			v5Calls++
			v5DeviceID = r.URL.Query().Get("device_id")
			v5InstallID = r.URL.Query().Get("iid")
			v5CDID = r.URL.Query().Get("cdid")
			w.WriteHeader(http.StatusOK) // Deliberately empty, as in the reported failure.
		case protocol.PathPlaybackVideoTab:
			tabCalls++
			tabDeviceID = r.URL.Query().Get("device_id")
			tabInstallID = r.URL.Query().Get("iid")
			tabCDID = r.URL.Query().Get("cdid")
			if r.Method != http.MethodGet || r.URL.Query().Get("video_id") != episodeID ||
				r.URL.Query().Get("version_code") != "73932" ||
				r.Header.Get("Content-Type") != "" || r.Header.Get("x-ss-stub") != "" ||
				r.Header.Get("x-khronos") == "" || r.Header.Get("x-helios") == "" ||
				r.Header.Get("x-medusa") == "" {
				badTabRequest = fmt.Sprintf("method=%s path=%s query=%s headers=%v", r.Method, r.URL.Path, r.URL.RawQuery, r.Header)
			}
			_, _ = fmt.Fprintf(w, "{\\\"code\\\":0,\\\"data\\\":{\\\"video_model_data\\\":{\\\"%s\\\":{\\\"video_model\\\":{\\\"video_id\\\":\\\"%s\\\",\\\"video_list\\\":[{\\\"main_url\\\":\\\"https://cdn.example/video.mp4\\\",\\\"definition\\\":\\\"720P\\\",\\\"codec_type\\\":\\\"h264\\\"}]}}}}}", episodeID, episodeID)
		case protocol.PathPlayback:
			legacyCalls++
			_, _ = w.Write(fixture(t, "clear-playback.json"))
		default:
			http.NotFound(w, r)
		}
	}))
	defer srv.Close()
	logs := &syncLog{}
	client, err := sdk.NewClient(sdk.Options{
		Transport: httptransport.New(), ProfileStore: memstore.New(),
		APIOrigin: srv.URL, RegisterOrigin: srv.URL, DistributionOrigin: srv.URL,
		Logger: logs, DeviceResets: sdk.Int(0),
		Clock: func() time.Time { return time.UnixMilli(1_700_000_000_123).UTC() },
	})
	if err != nil {
		t.Fatal(err)
	}
	pb, err := client.ResolvePlayback(context.Background(), episodeID, sdk.PlaybackOptions{})
	if err != nil || pb == nil {
		t.Fatalf("video_tab recovery result = %v, error = %v", pb, err)
	}
	if registers != 1 || v5Calls != 1 || tabCalls != 1 || legacyCalls != 0 {
		t.Fatalf("registers=%d v5=%d video_tab=%d legacy=%d", registers, v5Calls, tabCalls, legacyCalls)
	}
	if badTabRequest != "" {
		t.Fatalf("video_tab request is not the required signed bodyless GET: %s", badTabRequest)
	}
	if v5DeviceID == "" || tabDeviceID != v5DeviceID ||
		tabInstallID != v5InstallID || tabCDID != v5CDID {
		t.Fatalf("the recovery request changed identity: V5=(%s,%s,%s) video_tab=(%s,%s,%s)",
			v5DeviceID, v5InstallID, v5CDID, tabDeviceID, tabInstallID, tabCDID)
	}
	if len(pb.Streams) != 1 || pb.Streams[0].URL != "https://cdn.example/video.mp4" {
		t.Fatalf("unexpected recovered streams: %+v", pb.Streams)
	}
}

func TestEmptyPlaybackMovesToFreshDevice(t *testing.T) {""",
)

for path in (
    "sdk/errors.go",
    "sdk/client.go",
    "sdk/errors_test.go",
    "sdk/catalog_test.go",
    "sdk/media.go",
    "sdk/reset.go",
    "sdk/reset_test.go",
    "internal/protocol/playback_v5.go",
    "internal/protocol/parse.go",
    "bindings/internal/dispatch/dispatch.go",
    "bindings/internal/dispatch/dispatch_test.go",
):
    subprocess.run(["gofmt", "-w", str(ROOT / path)], check=True)

print("Patched openhongguo v0.4.0 with structured diagnostics and signed video_tab playback recovery.")
