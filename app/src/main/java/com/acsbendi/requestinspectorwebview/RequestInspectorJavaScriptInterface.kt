package com.acsbendi.requestinspectorwebview

import android.util.Log
import android.webkit.JavascriptInterface
import android.webkit.WebView
import org.intellij.lang.annotations.Language

internal class RequestInspectorJavaScriptInterface {

    private val recordedRequests = ArrayList<RecordedRequest>()

    fun findRecordedRequestForUrl(url: String): RecordedRequest? {
        return recordedRequests.find { recordedRequest ->
            recordedRequest.url == url
        }
    }

    enum class RequestType {
        FETCH, XML_HTTP, FORM
    }

    data class RecordedRequest(
        val type: RequestType,
        val url: String,
        val method: String,
        val body: String,
        val headers: String,
        val trace: String,
        val enctype: String?
    ) {
        override fun toString(): String {
            val enctypeString = if (enctype != null) '"'.toString() + enctype.replace(
                "\"",
                "\\\""
            ) + '"' else "null"
            return "{ " +
                    "\"type\": \"" + type.toString().replace("\"", "\\\"") + '"' +
                    ", \"url\": \"" + url.replace("\"", "\\\"") + '"' +
                    ", \"method\": \"" + method.replace("\"", "\\\"") + '"' +
                    ", \"body\": \"" + body.replace("\"", "\\\"") + '"' +
                    ", \"headers\": \"" + headers.replace("\"", "\\\"") + '"' +
                    ", \"trace\": \"" + trace.replace("\"", "\\\"") + '"' +
                    ", \"enctype\": " + enctypeString +
                    " }"
        }
    }

    @JavascriptInterface
    fun recordFormSubmission(url: String,
        method: String,
        body: String,
        headers: String,
        trace: String,
        enctype: String?
    ) {
        Log.i(LOG_TAG, "Recorded form submission from JavaScript")
        recordedRequests.add(
            RecordedRequest(RequestType.FORM, url, method, body, headers, trace, enctype)
        )
    }

    @JavascriptInterface
    fun recordXhr(url: String, method: String, body: String, headers: String, trace: String) {
        Log.i(LOG_TAG, "Recorded XHR from JavaScript")
        recordedRequests.add(
            RecordedRequest(RequestType.XML_HTTP, url, method, body, headers, trace, null)
        )
    }

    @JavascriptInterface
    fun recordFetch(url: String, method: String, body: String, headers: String, trace: String) {
        Log.i(LOG_TAG, "Recorded fetch from JavaScript")
        recordedRequests.add(
            RecordedRequest(RequestType.FETCH, url, method, body, headers, trace, null)
        )
    }

    companion object {
        private const val LOG_TAG = "RequestInspectorJs"
        @Language("JS")
        private const val JAVASCRIPT_INTERCEPTION_CODE = """
function recordFormSubmission(form) {
    var jsonArr = [];
    for (i = 0; i < form.elements.length; i++) {
        var parName = form.elements[i].name;
        var parValue = form.elements[i].value;
        var parType = form.elements[i].type;

        jsonArr.push({
            name: parName,
            value: parValue,
            type: parType
        });
    }

    const path = form.attributes['action'] === undefined ? "" : form.attributes['action'].nodeValue;
    const method = form.attributes['method'] === undefined ? "GET" : form.attributes['method'].nodeValue;
    const host = location.protocol + '//' + location.host;
    const encType = form.attributes['enctype'] === undefined ? null : form.attributes['enctype'].nodeValue;
    const err = new Error();
    RequestInspection.recordFormSubmission(
        host + path,
        method,
        JSON.stringify(jsonArr),
        "",
        err.stack,
        encType
    );
}

function handleFormSubmission(e) {
    const form = e ? e.target : this;
    recordFormSubmission(form);
    form._submit();
}

HTMLFormElement.prototype._submit = HTMLFormElement.prototype.submit;
HTMLFormElement.prototype.submit = handleFormSubmission;
window.addEventListener('submit', function (submitEvent) {
    handleFormSubmission(submitEvent);
}, true);

let lastXmlhttpRequestPrototypeMethod = null;
let xmlhttpRequestHeaders = "";
let xmlhttpRequestUrl = null;
XMLHttpRequest.prototype._open = XMLHttpRequest.prototype.open;
XMLHttpRequest.prototype.open = function (method, url, async, user, password) {
    lastXmlhttpRequestPrototypeMethod = method;
    xmlhttpRequestUrl = url;
    this._open(method, url, async, user, password);
};
XMLHttpRequest.prototype._setRequestHeader = XMLHttpRequest.prototype.setRequestHeader;
XMLHttpRequest.prototype.setRequestHeader = function (header, value) {
    xmlhttpRequestHeaders += (header + ": " + value + "\n");
    this._setRequestHeader(header, value);
};
XMLHttpRequest.prototype._send = XMLHttpRequest.prototype.send;
XMLHttpRequest.prototype.send = function (body) {
    let err = new Error();
    RequestInspection.recordXhr(
        xmlhttpRequestUrl,
        lastXmlhttpRequestPrototypeMethod,
        body,
        xmlhttpRequestHeaders,
        err.stack
    );
    lastXmlhttpRequestPrototypeMethod = null;
    xmlhttpRequestUrl = null;
    xmlhttpRequestHeaders = "";
    this._send(fixedBody);
};

window._fetch = window.fetch;
window.fetch = function () {
    const url = arguments[1] && 'url' in arguments[1] ? arguments[1]['url'] : "";
    const method = arguments[1] && 'method' in arguments[1] ? arguments[1]['method'] : "GET";
    const body = arguments[1] && 'body' in arguments[1] ? arguments[1]['body'] : "";
    const headers = JSON.stringify(arguments[1] && 'headers' in arguments[1] ? arguments[1]['headers'] : {});
    let err = new Error();
    RequestInspection.recordFetch(url, method, body, headers, err.stack);
    return window._fetch.apply(this, arguments);
}
        """

        fun enableInterception(webView: WebView, extraJavaScriptToInject: String) {
            webView.evaluateJavascript(
                "javascript: $JAVASCRIPT_INTERCEPTION_CODE\n$extraJavaScriptToInject",
                null
            )
        }
    }
}