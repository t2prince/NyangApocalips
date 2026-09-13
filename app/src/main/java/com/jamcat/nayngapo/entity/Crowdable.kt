package com.jamcat.nayngapo.entity

/**
 * 서로 밀어내야 하는 대상(냥이들). 한자리에 오래 겹쳐 서 있지 않도록
 * GamePlayScreen이 이 정보를 보고 옆으로 비켜서게 한다.
 */
interface Crowdable {
    var x: Float
    val width: Float
    /** 다른 냥이와 겹쳐 있는 시간(초). 겹침이 풀리면 0으로 돌아간다 */
    var crowdTimer: Float
}
