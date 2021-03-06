package re.spitfy.ctftime.di

import dagger.Subcomponent
import dagger.android.AndroidInjector
import re.spitfy.ctftime.presentation.MainActivity

@Subcomponent
interface MainActivitySubComponent : AndroidInjector<MainActivity> {

    @Subcomponent.Builder
    abstract class Builder : AndroidInjector.Builder<MainActivity>()
}