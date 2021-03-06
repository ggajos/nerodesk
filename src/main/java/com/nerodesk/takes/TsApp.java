/**
 * Copyright (c) 2015, nerodesk.com
 * All rights reserved.
 *
 * Redistribution and use in source and binary forms, with or without
 * modification, are permitted provided that the following conditions
 * are met: 1) Redistributions of source code must retain the above
 * copyright notice, this list of conditions and the following
 * disclaimer. 2) Redistributions in binary form must reproduce the above
 * copyright notice, this list of conditions and the following
 * disclaimer in the documentation and/or other materials provided
 * with the distribution. 3) Neither the name of the nerodesk.com nor
 * the names of its contributors may be used to endorse or promote
 * products derived from this software without specific prior written
 * permission.
 *
 * THIS SOFTWARE IS PROVIDED BY THE COPYRIGHT HOLDERS AND CONTRIBUTORS
 * "AS IS" AND ANY EXPRESS OR IMPLIED WARRANTIES, INCLUDING, BUT
 * NOT LIMITED TO, THE IMPLIED WARRANTIES OF MERCHANTABILITY AND
 * FITNESS FOR A PARTICULAR PURPOSE ARE DISCLAIMED. IN NO EVENT SHALL
 * THE COPYRIGHT HOLDER OR CONTRIBUTORS BE LIABLE FOR ANY DIRECT,
 * INDIRECT, INCIDENTAL, SPECIAL, EXEMPLARY, OR CONSEQUENTIAL DAMAGES
 * (INCLUDING, BUT NOT LIMITED TO, PROCUREMENT OF SUBSTITUTE GOODS OR
 * SERVICES; LOSS OF USE, DATA, OR PROFITS; OR BUSINESS INTERRUPTION)
 * HOWEVER CAUSED AND ON ANY THEORY OF LIABILITY, WHETHER IN CONTRACT,
 * STRICT LIABILITY, OR TORT (INCLUDING NEGLIGENCE OR OTHERWISE)
 * ARISING IN ANY WAY OUT OF THE USE OF THIS SOFTWARE, EVEN IF ADVISED
 * OF THE POSSIBILITY OF SUCH DAMAGE.
 */
package com.nerodesk.takes;

import com.jcabi.log.Logger;
import com.jcabi.log.VerboseProcess;
import com.jcabi.manifests.Manifests;
import com.nerodesk.om.Base;
import com.nerodesk.takes.doc.TsDoc;
import java.io.File;
import java.io.IOException;
import java.util.regex.Pattern;
import org.apache.commons.lang3.exception.ExceptionUtils;
import org.takes.Request;
import org.takes.Take;
import org.takes.Takes;
import org.takes.facets.auth.PsByFlag;
import org.takes.facets.auth.PsChain;
import org.takes.facets.auth.PsCookie;
import org.takes.facets.auth.PsFake;
import org.takes.facets.auth.PsLogout;
import org.takes.facets.auth.RqAuth;
import org.takes.facets.auth.TsAuth;
import org.takes.facets.auth.TsSecure;
import org.takes.facets.auth.codecs.CcCompact;
import org.takes.facets.auth.codecs.CcHex;
import org.takes.facets.auth.codecs.CcSafe;
import org.takes.facets.auth.codecs.CcSalted;
import org.takes.facets.auth.codecs.CcXOR;
import org.takes.facets.auth.social.PsFacebook;
import org.takes.facets.fallback.Fallback;
import org.takes.facets.fallback.RqFallback;
import org.takes.facets.fallback.TsFallback;
import org.takes.facets.flash.TsFlash;
import org.takes.facets.fork.FkAnonymous;
import org.takes.facets.fork.FkAuthenticated;
import org.takes.facets.fork.FkFixed;
import org.takes.facets.fork.FkHitRefresh;
import org.takes.facets.fork.FkParams;
import org.takes.facets.fork.FkRegex;
import org.takes.facets.fork.Target;
import org.takes.facets.fork.TsFork;
import org.takes.tk.TkHTML;
import org.takes.tk.TkRedirect;
import org.takes.ts.TsClasspath;
import org.takes.ts.TsFiles;
import org.takes.ts.TsGreedy;
import org.takes.ts.TsVerbose;
import org.takes.ts.TsWithType;
import org.takes.ts.TsWrap;

/**
 * App.
 *
 * @author Yegor Bugayenko (yegor@teamed.io)
 * @version $Id$
 * @since 0.2
 * @checkstyle ClassDataAbstractionCouplingCheck (500 lines)
 * @checkstyle ClassFanOutComplexityCheck (500 lines)
 * @checkstyle ExcessiveMethodLength (500 lines)
 * @todo #68:30min Error page should be an HTML page with stacktrace.
 *  See how it's done in Rultor (com.rultor.web.App.fallback).
 *  This implies adding velocity template and some styles.
 *  More details available in PR #99
 */
@SuppressWarnings({
    "PMD.UseUtilityClass", "PMD.ExcessiveImports",
    "PMD.ExcessiveMethodLength"
})
public final class TsApp extends TsWrap {

    /**
     * Ctor.
     * @param base Base
     * @throws IOException If something goes wrong.
     */
    public TsApp(final Base base) throws IOException {
        super(TsApp.make(base));
    }

    /**
     * Make itself.
     * @param base Base
     * @return Takes
     * @throws IOException If fails
     */
    @SuppressWarnings("PMD.DoNotUseThreads")
    public static Takes make(final Base base) throws IOException {
        final Takes fork = new TsFork(
            new FkParams(
                PsByFlag.class.getSimpleName(),
                Pattern.compile(".+"),
                new TkRedirect()
            ),
            new FkRegex(
                "/xsl/.*",
                new TsWithType(new TsClasspath(), "text/xsl")
            ),
            new FkRegex(
                "/css/.*",
                new TsWithType(
                    new TsFork(
                        new FkHitRefresh(
                            new File("./src/main/scss"),
                            new Runnable() {
                                @Override
                                public void run() {
                                    new VerboseProcess(
                                        new ProcessBuilder(
                                            "mvn",
                                            "sass:update-stylesheets",
                                            "minify:minify"
                                        )
                                    ).stdout();
                                }
                            },
                            new TsFiles("./target/classes")
                        ),
                        new FkFixed(new TsClasspath())
                    ),
                    "text/css"
                )
            ),
            new FkRegex("/robots.txt", ""),
            new FkRegex(
                "/",
                new TsFork(
                    new FkAuthenticated(
                        new Target<Request>() {
                            @Override
                            public Take route(final Request req)
                                throws IOException {
                                return new TkDocs(
                                    base.user(
                                        new RqAuth(req).identity().urn()
                                    ),
                                    req
                                );
                            }
                        }
                    ),
                    new FkAnonymous(
                        new Target<Request>() {
                            @Override
                            public Take route(final Request req) {
                                return new TkIndex(req);
                            }
                        }
                    )
                )
            ),
            new FkRegex(
                "/doc/.*",
                new TsSecure(new TsGreedy(new TsDoc(base)))
            )
        );
        return TsApp.fallback(
            new TsVerbose(new TsFlash(TsApp.auth(fork)))
        );
    }

    /**
     * Fallback.
     * @param takes Original takes
     * @return Takes with fallback
     */
    private static Takes fallback(final Takes takes) {
        return new TsFallback(
            takes,
            new Fallback() {
                @Override
                public Take take(final RqFallback req) {
                    final String exc = ExceptionUtils.getStackTrace(
                        req.throwable()
                    );
                    Logger.info(this, "Exception thrown\n%s", exc);
                    return new TkHTML(
                        String.format("oops, something went wrong!\n%s", exc)
                    );
                }
            }
        );
    }

    /**
     * Authenticated.
     * @param takes Takes
     * @return Authenticated takes
     */
    private static Takes auth(final Takes takes) {
        final String key = Manifests.read("Nerodesk-FacebookId");
        return new TsAuth(
            takes,
            new PsChain(
                new PsFake(key.startsWith("XXXX") || key.startsWith("${")),
                new PsByFlag(
                    new PsByFlag.Pair(
                        PsFacebook.class.getSimpleName(),
                        new PsFacebook(
                            key,
                            Manifests.read("Nerodesk-FacebookSecret")
                        )
                    ),
                    new PsByFlag.Pair(
                        PsLogout.class.getSimpleName(),
                        new PsLogout()
                    )
                ),
                new PsCookie(
                    new CcSafe(
                        new CcHex(
                            new CcXOR(
                                new CcSalted(new CcCompact()),
                                Manifests.read("Nerodesk-SecurityKey")
                            )
                        )
                    )
                )
            )
        );
    }

}
