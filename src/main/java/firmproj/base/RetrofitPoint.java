package firmproj.base;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import soot.SootMethod;
import soot.Value;
import soot.Local;
import soot.tagkit.*;

import java.util.*;

public class RetrofitPoint {

    private static final Logger LOGGER = LoggerFactory.getLogger(RetrofitPoint.class);
    private final SootMethod method;
    private final List<AnnotationTag> methodAnnotations;
    private final List<AnnotationTag> parameterAnnotations;
    //private final HashSet<Value> interestingVariables;


    private static List<AnnotationTag> extractMethodAnnotations(SootMethod method) {
        List<AnnotationTag> mAn = new ArrayList<>();
        for (Tag tag : method.getTags()) {
            if (tag instanceof VisibilityAnnotationTag) {
                if(((VisibilityAnnotationTag) tag).hasAnnotations())
                mAn.addAll(((VisibilityAnnotationTag) tag).getAnnotations());
            }
        }
        return mAn;
    }

    private static List<AnnotationTag> extractParameterAnnotations(SootMethod method) {
        List<AnnotationTag> pAn = new ArrayList<>();
        for (Tag tag : method.getTags()) {
            if (tag instanceof VisibilityParameterAnnotationTag) {
                LOGGER.info(tag.toString());
                VisibilityParameterAnnotationTag parameterAnnotationTag = (VisibilityParameterAnnotationTag) tag;
                for (VisibilityAnnotationTag vtag : parameterAnnotationTag.getVisibilityAnnotations()) {
                    if(vtag == null) break;
                    LOGGER.info("vtag: " + vtag.toString());
                    if(vtag.hasAnnotations())
                        pAn.addAll(vtag.getAnnotations());
                }
            }
        }
        return pAn;
    }



    public RetrofitPoint(SootMethod method){
        this(method, extractMethodAnnotations(method), extractParameterAnnotations(method));
    }

    public RetrofitPoint(SootMethod method, List<AnnotationTag> mAn, List<AnnotationTag> pAn){
        this.method = method;
        this.methodAnnotations = mAn;
        this.parameterAnnotations = pAn;
    }

    public SootMethod getMethod(){
        return this.method;
    }

    public List<AnnotationTag> getMethodAnnotations() {
        return methodAnnotations;
    }

    public List<AnnotationTag> getParameterAnnotations() {
        return parameterAnnotations;
    }

    public String toString() {
        StringBuilder result = new StringBuilder();
        result.append("RetrofitPoint: ");
        result.append(getMethod().toString());
        result.append(':');
        for(AnnotationTag tag: getMethodAnnotations()){
            result.append(tag.toString());
        }
        result.append(':');
        for(AnnotationTag tag: getParameterAnnotations()){
            result.append(tag.toString());
        }

        return result.toString();
    }
}