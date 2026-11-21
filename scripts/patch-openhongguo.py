#!/usr/bin/env python3
"""Apply Kaixinguo's Android diagnostics extension to pinned openhongguo v0.4.0.

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
    "sdk/device.go",
    '''"crypto/rand"
	"encoding/hex"
	"io"
	"time"''',
    '''"crypto/rand"
	"crypto/sha256"
	"encoding/hex"
	"fmt"
	"io"
	"time"''',
)

replace_once(
    "sdk/device.go",
    "func uuid(b []byte) string {",
    """// profileDiagnostic returns comparable, non-secret fingerprints and the
// configured device descriptor. It never emits raw IDs, cookies or tokens.
func profileDiagnostic(p *DeviceProfile) string {
	if p == nil {
		return "profile=missing"
	}
	local := sha256.Sum256([]byte(fmt.Sprintf("%s\\x00%s\\x00%s\\x00%s\\x00%s",
		p.CDID, p.OpenUDID, p.ClientUDID, p.OAID, p.ReqID)))
	serverFP := "missing"
	if p.DeviceID != "" || p.InstallID != "" {
		server := sha256.Sum256([]byte(p.DeviceID + "\\x00" + p.InstallID))
		serverFP = hex.EncodeToString(server[:6])
	}
	device := "unknown"
	if p.Device != nil {
		d := p.Device
		device = fmt.Sprintf("%s/%s os=%s api=%d build=%s resolution=%s dpi=%d channel=%s",
			d.Brand, d.Model, d.OSVersion, d.OSAPI, d.Build, d.Resolution, d.DPI, d.Channel)
	}
	return fmt.Sprintf(
		"local_fp=%s server_fp=%s created_at=%s device={%s} device_id_present=%t install_id_present=%t guest_ticket_present=%t store_region_present=%t",
		hex.EncodeToString(local[:6]), serverFP, p.CreatedAt.UTC().Format(time.RFC3339Nano),
		device, p.DeviceID != "" && p.DeviceID != "0", p.InstallID != "" && p.InstallID != "0",
		p.TTReq != "", p.StoreRegion != "",
	)
}

func uuid(b []byte) string {""",
)

replace_once(
    "sdk/client.go",
    """	if err := c.profileStore.Save(ctx, p); err != nil {
		return nil, err
	}
	return p, nil
}

func guestReady""",
    """	if err := c.profileStore.Save(ctx, p); err != nil {
		return nil, err
	}
	if c.logger != nil {
		c.logger.Printf("anonymous registration succeeded: %s", profileDiagnostic(p))
	}
	return p, nil
}

func guestReady""",
)

replace_once(
    "sdk/reset.go",
    """func (c *Client) resetDevice(ctx context.Context) error {
	if c.profileStore == nil {
		return ErrInvalidOptions
	}
	if err := c.profileStore.Reset(ctx); err != nil {
		return err
	}
	_, err := c.ensureGuest(ctx)
	return err
}""",
    """func (c *Client) resetDevice(ctx context.Context) error {
	if c.profileStore == nil {
		return ErrInvalidOptions
	}
	if before, err := c.profileStore.Load(ctx); err == nil && c.logger != nil {
		c.logger.Printf("anonymous profile before reset: %s", profileDiagnostic(before))
	}
	if err := c.profileStore.Reset(ctx); err != nil {
		if c.logger != nil {
			c.logger.Printf("anonymous profile reset failed: %v", err)
		}
		return err
	}
	after, err := c.ensureGuest(ctx)
	if err != nil {
		if c.logger != nil {
			c.logger.Printf("anonymous profile after reset: registration failed: %v", err)
		}
		return err
	}
	if c.logger != nil {
		c.logger.Printf("anonymous profile after reset: %s", profileDiagnostic(after))
	}
	return nil
}""",
)

replace_once(
    "sdk/media.go",
    """		var raw []byte
		for attempt := 0; attempt < 2; attempt++ {
			if err = ctx.Err(); err != nil {""",
    """		if c.logger != nil {
			c.logger.Printf("playback profile: %s", profileDiagnostic(p))
		}
		var raw []byte
		for attempt := 0; attempt < 2; attempt++ {
			if c.logger != nil {
				c.logger.Printf("playback V5 attempt %d/2", attempt+1)
			}
			if err = ctx.Err(); err != nil {""",
)

replace_once(
    "sdk/media.go",
    """			raw, _, err = c.doRaw(ctx, "POST", c.apiOrigin+protocol.PathPlaybackV5+"?"+query, headers, body)
			if ctxErr := ctx.Err(); ctxErr != nil {""",
    """			raw, _, err = c.doRaw(ctx, "POST", c.apiOrigin+protocol.PathPlaybackV5+"?"+query, headers, body)
			if err != nil && c.logger != nil {
				c.logger.Printf("playback V5 attempt %d/2 failed: %v", attempt+1, err)
			}
			if ctxErr := ctx.Err(); ctxErr != nil {""",
)

replace_once(
    "sdk/media.go",
    """			v5Err := err
			legacyBody, bodyErr := protocol.PlaybackBody(episodeID)""",
    """			v5Err := err
			if c.logger != nil {
				c.logger.Printf("playback legacy fallback after V5 failure: %v", v5Err)
			}
			legacyBody, bodyErr := protocol.PlaybackBody(episodeID)""",
)

replace_once(
    "sdk/media.go",
    """			ticket := c.clock().UnixMilli()
			query, headers := sign.SignPlaybackPOST(protocol.PathPlayback, protocol.DeviceQueryPairs(c.identity(p)), ticket, p.DeviceID, protocol.VersionName, legacyBody, nil)
			raw, _, err = c.doRaw(ctx, "POST", c.apiOrigin+protocol.PathPlayback+"?"+query, anonymousHeaders(c, p, ticket, headers), legacyBody)
			if ctxErr := ctx.Err(); ctxErr != nil {""",
    """			ticket := c.clock().UnixMilli()
			query, headers := sign.SignPlaybackPOST(protocol.PathPlayback, protocol.DeviceQueryPairs(c.identity(p)), ticket, p.DeviceID, protocol.VersionName, legacyBody, nil)
			if c.logger != nil {
				c.logger.Printf("playback legacy attempt")
			}
			raw, _, err = c.doRaw(ctx, "POST", c.apiOrigin+protocol.PathPlayback+"?"+query, anonymousHeaders(c, p, ticket, headers), legacyBody)
			if err != nil && c.logger != nil {
				c.logger.Printf("playback legacy attempt failed: %v", err)
			}
			if ctxErr := ctx.Err(); ctxErr != nil {""",
)

with (ROOT / "sdk/profile_diagnostic_test.go").open("w", encoding="utf-8") as f:
    f.write("""package sdk

import (
	"strings"
	"testing"
	"time"

	"github.com/beilunyang/openhongguo/sdk/model"
)

func TestProfileDiagnosticIsComparableWithoutExposingIdentifiers(t *testing.T) {
	p := &DeviceProfile{
		CDID: "secret-cdid-value", OpenUDID: "secret-openudid",
		ClientUDID: "secret-clientudid", OAID: "secret-oaid",
		ReqID: "secret-reqid", DeviceID: "server-device-secret",
		InstallID: "server-install-secret", DeviceToken: "never-log-this",
		CreatedAt: time.Unix(10, 0).UTC(),
		Device: &model.Device{Brand: "Xiaomi", Model: "23127PN0CC"},
	}
	got := profileDiagnostic(p)
	for _, secret := range []string{
		p.CDID, p.OpenUDID, p.ClientUDID, p.OAID, p.ReqID,
		p.DeviceID, p.InstallID, p.DeviceToken,
	} {
		if strings.Contains(got, secret) {
			t.Fatalf("diagnostic leaked raw identity %q: %s", secret, got)
		}
	}
	if !strings.Contains(got, "local_fp=") || !strings.Contains(got, "server_fp=") ||
		!strings.Contains(got, "Xiaomi/23127PN0CC") {
		t.Fatalf("diagnostic missed comparable metadata: %s", got)
	}
	q := *p
	q.CDID = "a-different-local-id"
	if profileDiagnostic(&q) == got {
		t.Fatal("different local identity produced the same diagnostic fingerprint")
	}
}
""")

for path in (
    "sdk/errors.go",
    "sdk/device.go",
    "sdk/client.go",
    "sdk/reset.go",
    "sdk/media.go",
    "sdk/profile_diagnostic_test.go",
    "sdk/errors_test.go",
    "sdk/catalog_test.go",
    "bindings/internal/dispatch/dispatch.go",
    "bindings/internal/dispatch/dispatch_test.go",
):
    subprocess.run(["gofmt", "-w", str(ROOT / path)], check=True)

print("Patched openhongguo v0.4.0 with structured HTTP/business and anonymous-profile diagnostics.")
