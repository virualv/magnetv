package cn.byteorchid.magnetv.service;


import cn.byteorchid.magnetv.config.ApplicationConfig;
import cn.byteorchid.magnetv.exception.MagnetParserException;
import cn.byteorchid.magnetv.request.DefaultSslSocketFactory;
import net.sf.ehcache.Cache;
import net.sf.ehcache.CacheManager;
import net.sf.ehcache.Element;

import org.apache.log4j.Logger;
import org.htmlcleaner.CleanerProperties;
import org.htmlcleaner.DomSerializer;
import org.htmlcleaner.HtmlCleaner;
import org.htmlcleaner.TagNode;
import org.jsoup.Connection;
import org.jsoup.Jsoup;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.scheduling.annotation.Async;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;
import org.w3c.dom.Document;
import org.w3c.dom.Node;
import org.w3c.dom.NodeList;

import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Proxy;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.naming.ConfigurationException;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.xpath.XPath;
import javax.xml.xpath.XPathConstants;
import javax.xml.xpath.XPathFactory;

import cn.byteorchid.magnetv.response.MagnetItem;
import cn.byteorchid.magnetv.response.MagnetItemDetail;
import cn.byteorchid.magnetv.response.MagnetPageOption;
import cn.byteorchid.magnetv.response.MagnetPageSiteSort;
import cn.byteorchid.magnetv.response.MagnetRule;
import cn.byteorchid.magnetv.response.MagnetRuleDetail;

/**
 * created 2018/3/6 16:04
 */
@EnableAsync
@Service
public class MagnetService {
    private Logger logger = Logger.getLogger(getClass());

    @Autowired
    private MagnetRuleService ruleService;

    @Autowired
    private ApplicationConfig config;

    @CacheEvict(value = {"magnetList", "magnetDetail"}, allEntries = true)
    public void clearCache() {
        logger.info("??????????????????");
    }


    /**
     * ????????????
     *
     * @param sourceParam
     * @param keyword
     * @param sortParam
     * @param pageParam
     * @return
     */
    public MagnetPageOption transformCurrentOption(String sourceParam, String keyword,
                                                   String sortParam, Integer pageParam) {
        MagnetPageOption option = new MagnetPageOption();

        option.setKeyword(keyword);
        int page = pageParam == null || pageParam <= 0 ? 1 : pageParam;
        option.setPage(page);

        //??????????????????????????? ????????? ?????????????????????
        MagnetRule source = ruleService.getRuleBySite(sourceParam);
        option.setSite(source.getSite());

        //???????????????????????? ??????????????????????????????
        List<MagnetPageSiteSort> supportedSorts = ruleService.getSupportedSorts(source.getPaths());
        for (MagnetPageSiteSort item : supportedSorts) {
            if (item.getSort().equals(sortParam)) {
                option.setSort(item.getSort());
                break;
            }
        }
        if (StringUtils.isEmpty(option.getSort())) {
            option.setSort(supportedSorts.get(0).getSort());
        }

        String url = formatSiteUrl(source, option.getKeyword(), option.getSort(), option.getPage());
        option.setSiteUrl(url);
        return option;
    }


    /**
     * ????????????
     *
     * @param url
     * @param site      ????????????
     * @param host      ???????????????
     * @param isProxy   ??????????????????
     * @param userAgent
     * @return
     * @throws IOException
     */
    protected Document requestSourceSite(String url, String site, String host, boolean isProxy, String userAgent) throws IOException, ParserConfigurationException {
        Connection connect = Jsoup.connect(url)
                .ignoreContentType(true)
                .sslSocketFactory(DefaultSslSocketFactory.getDefaultSslSocketFactory())
                .timeout((int) config.sourceTimeout)
                .header(HttpHeaders.HOST, host);
        //??????userAgent
        if (StringUtils.isEmpty(userAgent)) {
            connect.header(HttpHeaders.USER_AGENT, userAgent);
        }

        //????????????
        if (config.proxyEnabled && isProxy) {
            Proxy.Type proxyType = null;
            if (config.proxyTypeSting != null) {
                String proxyTypeSting = config.proxyTypeSting;

                if (proxyTypeSting.equalsIgnoreCase("http")) {
                    proxyType = Proxy.Type.HTTP;
                } else if (proxyTypeSting.equalsIgnoreCase("socks")) {
                    proxyType = Proxy.Type.SOCKS;
                } else {
                    logger.error(proxyTypeSting + " is invalid !");
                }
            }
            Proxy proxy = new Proxy(proxyType, new InetSocketAddress(config.proxyHost, config.proxyPort));
            connect.proxy(proxy);
        }

        StringBuffer log = new StringBuffer();
        log.append("????????????--->");
        log.append(site);
        log.append("--->");
        log.append(Thread.currentThread().getName());
        log.append("\n");
        log.append(url);
        log.append("\n[Request Headers]\n");
        Map<String, String> headers = connect.request().headers();
        for (Map.Entry<String, String> entry : headers.entrySet()) {
            String header = entry.getKey();
            log.append(header);
            log.append(":");
            log.append(headers.get(header));
            log.append("\n");
        }
        logger.info(log.toString());

        Connection.Response response = connect.execute();

        String html = response.parse().html();

        TagNode node = new HtmlCleaner().clean(html);
        return new DomSerializer(new CleanerProperties()).createDOM(node);
    }

    public String formatSiteUrl(MagnetRule rule, String keyword, String sort, int page) {
        if (StringUtils.isEmpty(keyword)){
            return rule.getUrl();
        }
        //????????????????????? ???????????????url
        //??????????????????????????????2.1.1???????????????????????????
        String sortPath = ruleService.getPathBySort(sort, rule.getPaths()).replace("%s", keyword).replace("%d", String.valueOf(page));
        return String.format("%s%s", rule.getUrl(), sortPath);
    }

    @Cacheable(value = "magnetList", key = "T(String).format('%s-%s-%s-%d',#rule.url,#keyword,#sort,#page)")
    public List<MagnetItem> parser(MagnetRule rule, String keyword, String sort, int page, String userAgent) throws MagnetParserException, IOException {
        if (StringUtils.isEmpty(keyword)) {
            return new ArrayList<MagnetItem>();
        }

        String url = formatSiteUrl(rule, keyword, sort, page);

        //????????????
        try {
            Document dom = requestSourceSite(url, rule.getSite(), rule.getHost(), rule.isProxy(), userAgent);

            List<MagnetItem> infos = new ArrayList<MagnetItem>();
            XPath xPath = XPathFactory.newInstance().newXPath();

            //??????
            NodeList result = (NodeList) xPath.evaluate(rule.getGroup(), dom, XPathConstants.NODESET);
            for (int i = 0; i < result.getLength(); i++) {
                Node node = result.item(i);
                if (node != null) {
                    if (StringUtils.isEmpty(node.getTextContent().trim())) {
                        continue;
                    }
                    MagnetItem info = new MagnetItem();
                    //?????????
                    Node magnetNote = (Node) xPath.evaluate(rule.getMagnet(), node, XPathConstants.NODE);
                    if (magnetNote != null) {
                        String magnetValue = magnetNote.getTextContent();
                        info.setMagnet(transformMagnet(magnetValue));
                    }
                    //??????
                    NodeList nameNotes = ((NodeList) xPath.evaluate(rule.getName(), node, XPathConstants.NODESET));
                    if (nameNotes != null && nameNotes.getLength() > 0) {
                        //???????????????????????????????????? ????????????????????? ??????Nyaa
                        Node nameNote = nameNotes.item(nameNotes.getLength() - 1);

                        String nameValue = nameNote.getTextContent();
                        info.setName(nameValue);
                        //??????????????? ???????????????
                        int keywordIndex = nameValue.toLowerCase().indexOf(keyword.toLowerCase());
                        if (keywordIndex >= 0) {
                            StringBuilder buffer = new StringBuilder(nameValue);
                            buffer.insert(keywordIndex + keyword.length(), "</span>");
                            buffer.insert(keywordIndex, "<span style=\"color:#ff7a76\">");
                            info.setNameHtml(buffer.toString());
                        } else {
                            info.setNameHtml(nameValue.replace(keyword, String.format("<span style=\"color:#ff7a76\">%s</span>", keyword)));
                        }

                        Node hrefAttr = nameNote.getAttributes().getNamedItem("href");
                        if (hrefAttr != null) {
                            info.setDetailUrl(transformDetailUrl(rule.getUrl(), hrefAttr.getTextContent()));
                        }

                        //???????????????????????????
                        String resolution = transformResolution(nameValue);
                        info.setResolution(resolution);
                    }
                    //??????
                    Node sizeNote = ((Node) xPath.evaluate(rule.getSize(), node, XPathConstants.NODE));
                    if (sizeNote != null) {
                        String sizeValue = sizeNote.getTextContent();
                        info.setFormatSize(sizeValue);
                        info.setSize(transformSize(sizeValue));
                    }
                    //??????
                    Node countNode = (Node) xPath.evaluate(rule.getDate(), node, XPathConstants.NODE);
                    if (countNode != null) {
                        info.setDate(countNode.getTextContent());
                    }
                    //??????/??????
                    if (!StringUtils.isEmpty(rule.getHot())) {
                        Node popularityNode = (Node) xPath.evaluate(rule.getHot(), node, XPathConstants.NODE);
                        if (popularityNode != null) {
                            info.setHot(popularityNode.getTextContent());
                        }
                    }

                    if (!StringUtils.isEmpty(info.getName())) {
                        infos.add(info);
                    }
                }
            }
            return infos;
        } catch (Exception e) {
            throw new MagnetParserException(e);
        }
    }


    /**
     * ?????????????????????
     */
    @Async
    public void asyncPreloadNextPage(MagnetRule rule, MagnetPageOption current, String userAgent) {
        try {
            int page = current.getPage() + 1;
            String cacheName = "magnetList";
            if (CacheManager.getInstance().cacheExists(cacheName)) {
                String key = String.format("%s-%s-%s-%d", rule.getUrl(), current.getKeyword(), current.getSort(), page);
                Cache cache = CacheManager.getInstance().getCache(cacheName);
                Element element = cache.get(key);
                //?????????????????? ??????????????????
                if (element == null) {
                    List<MagnetItem> items = this.parser(rule, current.getKeyword(), current.getSort(), page, userAgent);
                    cache.put(new Element(key, items));

                    logger.info(String.format("??????????????? %s-%s-%d?????????%d?????????", current.getSite(), current.getKeyword(), page, items.size()));
                }
            }
        } catch (MagnetParserException e) {
            e.printStackTrace();
        } catch (IOException e) {
            e.printStackTrace();
        }
    }


    /**
     * ??????????????????
     *
     * @param detailUrl ????????????url
     * @param rule
     * @param userAgent
     * @return
     * @throws MagnetParserException
     */
    @Cacheable(value = "magnetDetail", key = "#detailUrl")
    public MagnetItemDetail parserDetail(String detailUrl, MagnetRule rule, String userAgent) throws MagnetParserException {
        try {
            MagnetRuleDetail detail = rule.getDetail();
            if (detail == null) {
                throw new NullPointerException("?????????????????????????????????");
            }
            Document dom = requestSourceSite(detailUrl, rule.getSite(), rule.getHost(), rule.isProxy(), userAgent);
            XPath xPath = XPathFactory.newInstance().newXPath();

            MagnetItemDetail result = new MagnetItemDetail();
            //????????????
            List<String> files = new ArrayList<String>();
            NodeList fileNodeList = (NodeList) xPath.evaluate(detail.getFiles(), dom, XPathConstants.NODESET);
            for (int i = 0; i < fileNodeList.getLength(); i++) {
                Node item = fileNodeList.item(i);
                if (item != null) {
                    files.add(item.getTextContent().trim());
                }
            }
            result.setFiles(files);
            return result;
        } catch (Exception e) {
            throw new MagnetParserException(e);
        }
    }

    private String transformDetailUrl(String url, String magnetValue) {
        return magnetValue.startsWith("http") ? magnetValue : url + magnetValue;
    }

    /**
     * ???????????????
     * ??????url???????????????????????????????????????????????????
     *
     * @param url
     * @return
     */
    private String transformMagnet(String url) {
        if (StringUtils.isEmpty(url)) {
            return url;
        }
        String regex = "magnet:?[^\\\"]+";
        boolean matches = Pattern.matches(regex, url);
        if (matches) {
            return url;
        } else {
            String newMagnet;
            try {
                StringBuffer sb = new StringBuffer(url);
                int htmlIndex = url.lastIndexOf(".html");
                if (htmlIndex != -1) {
                    sb.delete(htmlIndex, sb.length());
                }
                int paramIndex = url.indexOf("&");
                if (paramIndex != -1) {
                    sb.delete(paramIndex, sb.length());
                }
                if (sb.length() >= 40) {
                    newMagnet = sb.substring(sb.length() - 40, sb.length());
                } else {
                    newMagnet = url;
                }
            } catch (Exception e) {
                e.printStackTrace();
                newMagnet = url;
            }
            return String.format("magnet:?xt=urn:btih:%s", newMagnet);
        }
    }


    /**
     * ???????????????????????????
     *
     * @param name
     * @return
     */
    private String transformResolution(String name) {
        String lowerName = name.toLowerCase();
        String regex4k = ".*(2160|4k).*";
        String regex720 = ".*(1280|720p|720P).*";
        String regex1080 = ".*(1920|1080p|1080P).*";
        boolean matches720 = Pattern.matches(regex720, lowerName);
        if (matches720) {
            return "720P";
        }
        boolean matches1080 = Pattern.matches(regex1080, lowerName);
        if (matches1080) {
            return "1080P";
        }
        boolean matches4k = Pattern.matches(regex4k, lowerName);
        if (matches4k) {
            return "4K";
        }
        return "";
    }


    /**
     * ??????????????????????????????
     *
     * @param formatSize
     * @return
     */
    private long transformSize(String formatSize) {
        try {
            long baseNumber = 0;
            if (formatSize.contains("G")) {
                baseNumber = 1024 * 1024 * 1024;
            } else if (formatSize.contains("M")) {
                baseNumber = 1024 * 1024;
            } else if (formatSize.contains("K")) {
                baseNumber = 1024;
            }
            Matcher matcher = Pattern.compile("(\\d+(\\.\\d+)?)").matcher(formatSize);
            if (matcher.find()) {
                String newFormatSize = matcher.group();
                float size = Float.parseFloat(newFormatSize);
                return (long) (size * baseNumber);
            }
        } catch (NumberFormatException e) {
        }
        return 0L;
    }


}
