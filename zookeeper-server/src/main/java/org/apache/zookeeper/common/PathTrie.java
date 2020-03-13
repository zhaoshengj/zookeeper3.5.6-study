 /**
 * Licensed to the Apache Software Foundation (ASF) under one
 * or more contributor license agreements.  See the NOTICE file
 * distributed with this work for additional information
 * regarding copyright ownership.  The ASF licenses this file
 * to you under the Apache License, Version 2.0 (the
 * "License"); you may not use this file except in compliance
 * with the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.apache.zookeeper.common;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * a class that implements prefix matching for 
 * components of a filesystem path. the trie
 * looks like a tree with edges mapping to 
 * the component of a path.
 * example /ab/bc/cf would map to a trie
 *           /
 *        ab/
 *        (ab)
 *      bc/
 *       / 
 *      (bc)
 *   cf/
 *   (cf)
 */
//1.addChild方法时，注意在调用方设置child的parent为当前节点
//2.deleteChild方法似乎有bug(或者说不准确),就是把儿子的儿子个数为1当成一种处理,0或者>=2当成另外一种处理
//3.property的意义:设置了配额的节点，该属性为true，否则为false
//比如说:设置了/a/b/c这个路径拥有配额，那么字典树中，property是这样的
//
//            root
//          /
//        a
//      /
//    b
//  /
//c(property:true)
public class PathTrie {
    /**
     * the logger for this class
     */
    private static final Logger LOG = LoggerFactory.getLogger(PathTrie.class);
    
    /**
     * the root node of PathTrie
     */
    private final TrieNode rootNode ;
    
    static class TrieNode {
        boolean property = false;//属性，看源码表现,就是设置了配额的节点
        final HashMap<String, TrieNode> children;//记录子节点相对路径 与 TrieNode的mapping
        TrieNode parent = null;
        /**
         * create a trienode with parent
         * as parameter
         * @param parent the parent of this trienode
         */
        private TrieNode(TrieNode parent) {//构造时，设置parent
            children = new HashMap<String, TrieNode>();
            this.parent = parent;
        }
        
        /**
         * get the parent of this node
         * @return the parent node
         */
        TrieNode getParent() {
            return this.parent;
        }
        
        /**
         * set the parent of this node
         * @param parent the parent to set to
         */
        void setParent(TrieNode parent) {
            this.parent = parent;
        }
        
        /**
         * a property that is set 
         * for a node - making it 
         * special.
         */
        void setProperty(boolean prop) {
            this.property = prop;
        }
        
        /** the property of this
         * node 
         * @return the property for this
         * node
         */
        boolean getProperty() {
            return this.property;
        }
        /**
         * add a child to the existing node
         * @param childName the string name of the child
         * @param node the node that is the child
         *//*
         * 添加childName的相对路径到map,注意:在调用方设置node的parent
         */
        void addChild(String childName, TrieNode node) {
            synchronized(children) {
                if (children.containsKey(childName)) {
                    return;
                }
                children.put(childName, node);
            }
        }
     
        /**
         * delete child from this node
         * @param childName the string name of the child to 
         * be deleted
         */
        void deleteChild(String childName) {
            synchronized(children) {
                if (!children.containsKey(childName)) {
                    return;
                }
                TrieNode childNode = children.get(childName);
                // this is the only child node.
                if (childNode.getChildren().length == 1) { //如果这个儿子只有1个儿子,那么就把这个儿子丢掉
                    childNode.setParent(null);//被删除的子节点,parent设置为空
                    children.remove(childName);
                }
                else {
                    // their are more child nodes
                    // so just reset property.
                    childNode.setProperty(false);//否则这个儿子还有其他儿子，标记它不是没有配额限制,这里有个bug，就是数量为0时，也进入这个逻辑
                }
            }
        }
        
        /**
         * return the child of a node mapping
         * to the input childname
         * @param childName the name of the child
         * @return the child of a node
         */
        TrieNode getChild(String childName) {
            synchronized(children) {
               if (!children.containsKey(childName)) {
                   return null;
               }
               else {
                   return children.get(childName);
               }
            }
        }

        /**
         * get the list of children of this 
         * trienode.
         * @param node to get its children
         * @return the string list of its children
         */
        String[] getChildren() {
           synchronized(children) {
               return children.keySet().toArray(new String[0]);
           }
        }
        
        /**
         * get the string representation
         * for this node
         */
        public String toString() {
            StringBuilder sb = new StringBuilder();
            sb.append("Children of trienode: ");
            synchronized(children) {
                for (String str: children.keySet()) {
                    sb.append(" " + str);
                }
            }
            return sb.toString();
        }
    }
    
    /**
     * construct a new PathTrie with
     * a root node of /
     */
    public PathTrie() {
        this.rootNode = new TrieNode(null);
    }
    
    /**
     * add a path to the path trie 
     * @param path
     */
    //字典树的增，这里就是把一个path按照/符号分开，加入字典树
    public void addPath(String path) {
        if (path == null) {
            return;
        }
        String[] pathComponents = path.split("/");//将路径按照"/" split开
        TrieNode parent = rootNode;
        String part = null;
        if (pathComponents.length <= 1) {
            throw new IllegalArgumentException("Invalid path " + path);
        }
        for (int i=1; i<pathComponents.length; i++) {//从1开始因为路径都是"/"开头的,pathComponents[0]会是""
            part = pathComponents[i];
            if (parent.getChild(part) == null) {
                parent.addChild(part, new TrieNode(parent));//一方面parent将这个child记录在map，一方面将这个child node进行初始化以及设置parent
            }
            parent = parent.getChild(part);//进入到对应的child
        }
        parent.setProperty(true);//最后这个节点设置配额属性
    }
    
    /**
     * delete a path from the trie
     * @param path the path to be deleted
     */
    public void deletePath(String path) {
        if (path == null) {
            return;
        }
        String[] pathComponents = path.split("/");
        TrieNode parent = rootNode;
        String part = null;
        if (pathComponents.length <= 1) { 
            throw new IllegalArgumentException("Invalid path " + path);
        }
        for (int i=1; i<pathComponents.length; i++) {
            part = pathComponents[i];
            if (parent.getChild(part) == null) {
                //the path does not exist 
                return;
            }
            parent = parent.getChild(part);
            LOG.info("{}",parent);
        }
        TrieNode realParent  = parent.getParent();//得到被删除TrieNode的parent
        realParent.deleteChild(part);//将这个node从parent的子列表中删除
    }
    
    /**
     * return the largest prefix for the input path.
     * @param path the input path
     * @return the largest prefix for the input path.
     */
    //找到最近的一个拥有配额标记的祖先节点
    public String findMaxPrefix(String path) {
        if (path == null) {
            return null;
        }
        if ("/".equals(path)) {
            return path;
        }
        String[] pathComponents = path.split("/");//按照/分开
        TrieNode parent = rootNode;
        List<String> components = new ArrayList<String>();
        if (pathComponents.length <= 1) {
            throw new IllegalArgumentException("Invalid path " + path);
        }
        int i = 1;//因为路径是/开头
        String part = null;
        StringBuilder sb = new StringBuilder();
        int lastindex = -1;
        while((i < pathComponents.length)) {
            if (parent.getChild(pathComponents[i]) != null) {
                part = pathComponents[i];
                parent = parent.getChild(part);//一层层到子节点
                components.add(part);
                if (parent.getProperty()) {//如果对应的子节点有标记
                    lastindex = i-1;//更新最后一个有标记的节点(也就是最近的有标记的祖先)
                }
            }
            else {
                break;
            }
            i++;
        }
        for (int j=0; j< (lastindex+1); j++) {
            sb.append("/" + components.get(j));
        }
        return sb.toString();
    }

    /**
     * clear all nodes
     */
    public void clear() {
        for(String child : rootNode.getChildren()) {
            rootNode.deleteChild(child);
        }
    }
}
