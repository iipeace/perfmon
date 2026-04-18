package com.oss.perfmon.di

import com.oss.perfmon.channel.TcpChannel
import com.oss.perfmon.monitor.ResourceMonitor
import com.oss.perfmon.probe.SystemProbe
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

// Hilt DI 모듈 — 앱 전체 생명주기(SingletonComponent)에 종속성을 등록한다
@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    // TcpChannel은 매 명령어마다 소켓을 새로 열고 닫으므로 Singleton이 아니다
    @Provides
    fun provideChannel(): TcpChannel = TcpChannel()

    // ResourceMonitor는 channel을 공유하므로 Singleton으로 관리
    @Provides
    @Singleton
    fun provideResourceMonitor(channel: TcpChannel): ResourceMonitor =
        ResourceMonitor(channel)

    // SystemProbe도 동일하게 Singleton으로 관리
    @Provides
    @Singleton
    fun provideSystemProbe(channel: TcpChannel): SystemProbe =
        SystemProbe(channel)
}
