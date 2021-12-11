package cn.byteorchid.magnetv.response;

import java.util.List;


public class MagnetPageData {

    private List<String> sites;
    private MagnetRule rule;
    private MagnetPageOption current;
    private List<MagnetItem> results;
    private String trackersString;
    private String aria2Rpc;
    private String aria2RpcToken;

    public MagnetRule getRule() {
        return rule;
    }

    public void setRule(MagnetRule rule) {
        this.rule = rule;
    }

    public List<String> getSites() {
        return sites;
    }

    public void setSites(List<String> sites) {
        this.sites = sites;
    }

    public MagnetPageOption getCurrent() {
        return current;
    }

    public void setCurrent(MagnetPageOption current) {
        this.current = current;
    }

    public List<MagnetItem> getResults() {
        return results;
    }

    public void setResults(List<MagnetItem> results) {
        this.results = results;
    }

    public String getTrackersString() {
        return trackersString;
    }

    public void setTrackersString(String trackersString) {
        this.trackersString = trackersString;
    }

    public String getAria2Rpc() {
        return aria2Rpc;
    }

    public void setAria2Rpc(String aria2Rpc) {
        this.aria2Rpc = aria2Rpc;
    }

    public String getAria2RpcToken() {
        return aria2RpcToken;
    }

    public void setAria2RpcToken(String aria2RpcToken) {
        this.aria2RpcToken = aria2RpcToken;
    }
}
